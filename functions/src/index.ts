// functions/src/index.ts
import * as admin from "firebase-admin";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {setGlobalOptions} from "firebase-functions/v2/options";

admin.initializeApp();

setGlobalOptions({
  region: "us-central1",
  timeoutSeconds: 60,
  memory: "256MiB",
});

const unique=<T>(arr:T[])=>{
  return Array.from(new Set(arr)).filter(Boolean) as T[];
};

/**
 * Envía notificaciones cuando se crea chats/{chatId}/messages/{messageId}.
 */
export const sendMessageNotification=onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event)=>{
    const messageId:string=event.params.messageId;
    const chatId:string=event.params.chatId;
    const snap=event.data;
    if (!snap) return;

    const messageData=snap.data() as {
      sender?:string;
    };
    const sender=(messageData.sender||"").trim();
    if (!sender) return;

    const db=admin.firestore();
    const chatDoc=await db.collection("chats").doc(chatId).get();
    if (!chatDoc.exists) return;

    const chat=chatDoc.data()||{};
    const type=((chat.type as string)||"direct").toLowerCase();
    const members:string[]=Array.isArray(chat.members)?chat.members:[];
    const targets=members.filter((email)=>email&&email!==sender);
    if (targets.length===0) return;

    const usersCol=db.collection("users");
    const userDocs=await Promise.all(targets.map((email)=>usersCol.doc(email).get()));

    const tokens:string[]=[];
    for (const doc of userDocs) {
      const data=doc.data()||{};
      const arr:string[]=Array.isArray(data.fcmTokens)?data.fcmTokens:[];
      for (const t of arr) if (t) tokens.push(t);
    }
    const uniqueTokens=unique(tokens);
    if (uniqueTokens.length===0) return;

    let senderName=sender.split("@")[0]||"Remitente";
    try {
      const senderDoc=await usersCol.doc(sender).get();
      const name=senderDoc.get("name") as string|undefined;
      if (name) senderName=name;
    } catch {
      // omitimos errores de lectura del remitente
    }

    const data:{[k:string]:string}={
      messageId,
      chatId,
      destType: type==="group"?"group":"private",
      sender,
      senderName,
    };

    if (type==="group") {
      data["peer"]=chatId;
      const groupName=(chat.name as string)||"Grupo";
      data["groupName"]=groupName;
    } else {
      data["peer"]=sender;
    }

    const message:admin.messaging.MulticastMessage={
      data,
      tokens: uniqueTokens,
      android: {priority: "high"},
      apns: {headers: {"apns-priority": "10"}},
    };

    try {
      const res=await admin.messaging().sendMulticast(message);

      const toDelete:string[]=[];
      res.responses.forEach((r, idx)=>{
        if (!r.success) {
          const err=r.error as {
            code?:string;
            errorInfo?:{code?:string};
          }|undefined;
          const code=(err&&(
            (err.errorInfo&&err.errorInfo.code)||err.code
          ))||"";
          if (code==="messaging/registration-token-not-registered") {
            toDelete.push(uniqueTokens[idx]);
          }
        }
      });

      if (toDelete.length>0) {
        await Promise.all(
          userDocs.map(async (docSnap)=>{
            const d=docSnap.data()||{};
            const arr:string[]=Array.isArray(d.fcmTokens)?d.fcmTokens:[];
            const filtered=arr.filter((t)=>!toDelete.includes(t));
            await docSnap.ref.update({fcmTokens: filtered});
          })
        );
      }
    } catch (e) {
      console.error("sendMulticast error", e);
    }
  }
);
