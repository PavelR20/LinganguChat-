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
 * Envía notificaciones cuando se crea /messages/{messageId}
 * (Firebase Functions v2).
 */
export const sendMessageNotification=onDocumentCreated(
  "messages/{messageId}",
  async (event)=>{
    const messageId:string=event.params.messageId;
    const snap=event.data;
    if (!snap) return;

    const msg=snap.data() as {
      sender?:string;
      receiver?:string;
      groupId?:string|null;
      participants?:string[];
    };

    const sender=(msg.sender||"").trim();
    const isGroup=!!msg.groupId&&String(msg.groupId).trim().length>0;

    let targets:string[]=[];
    if (isGroup) {
      const parts=Array.isArray(msg.participants)?msg.participants:[];
      targets=parts.filter((e)=>e&&e!==sender);
    } else {
      const receiver=(msg.receiver||"").trim();
      if (receiver&&receiver!==sender) targets=[receiver];
    }
    if (targets.length===0) return;

    const db=admin.firestore();
    const usersCol=db.collection("users");
    const userDocs=await Promise.all(
      targets.map((email)=>usersCol.doc(email).get())
    );

    const tokens:string[]=[];
    for (const doc of userDocs) {
      const data=doc.data()||{};
      const arr:string[]=Array.isArray(data.fcmTokens)?data.fcmTokens:[];
      for (const t of arr) if (t) tokens.push(t);
    }
    const uniqueTokens=unique(tokens);
    if (uniqueTokens.length===0) return;

    const senderName=sender.split("@")[0]||"Remitente";
    const data:{[k:string]:string}={
      messageId,
      destType: isGroup?"group":"private",
      sender,
      senderName,
    };

    if (isGroup) {
      const groupId=String(msg.groupId);
      data["peer"]=groupId;
      try {
        const gdoc=await db.collection("groups").doc(groupId).get();
        const groupName=(gdoc.exists&&(gdoc.get("name") as string))||"Grupo";
        data["groupName"]=groupName;
      } catch {
        // sin nombre de grupo si falla
      }
    } else {
      // En privados, el receptor debe abrir chat con el remitente
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
