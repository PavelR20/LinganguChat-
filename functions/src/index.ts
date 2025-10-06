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

type UserTokenDoc={
  snap:admin.firestore.DocumentSnapshot;
  tokens:string[];
};

const collectUserTokenDocs=async (
  emails:string[],
  usersCol:admin.firestore.CollectionReference
):Promise<UserTokenDoc[]>=>{
  const cleaned=unique(
    emails
      .map((email)=>typeof email==="string"?email.trim():"")
      .filter((email):email is string=>Boolean(email))
  );
  if (cleaned.length===0) return [];

  const snapshots=await Promise.all(
    cleaned.map((email)=>usersCol.doc(email).get())
  );

  return snapshots.map((snap)=>{
    const data=snap.data()||{};
    const arr=Array.isArray(data.fcmTokens)?data.fcmTokens:[];
    const tokens=arr
      .filter((token):token is string=>typeof token==="string")
      .map((token)=>token.trim())
      .filter((token)=>token.length>0);
    return {snap, tokens};
  });
};

const removeInvalidTokens=async (
  docs:UserTokenDoc[],
  invalidTokens:string[]
):Promise<void>=>{
  if (invalidTokens.length===0) return;
  await Promise.all(
    docs.map(async ({snap, tokens})=>{
      if (tokens.length===0) return;
      const filtered=tokens.filter((token)=>!invalidTokens.includes(token));
      if (filtered.length===tokens.length) return;
      await snap.ref.update({fcmTokens: filtered});
    })
  );
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
    const tokenDocs=await collectUserTokenDocs(targets, usersCol);
    const uniqueTokens=unique(tokenDocs.flatMap((item)=>item.tokens));
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
      event: "message",
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
      await removeInvalidTokens(tokenDocs, toDelete);
    } catch (e) {
      console.error("sendMulticast error", e);
    }
  }
);

/**
 * Envía notificaciones cuando se crea un chat en chats/{chatId}.
 */
export const sendChatCreatedNotification=onDocumentCreated(
  "chats/{chatId}",
  async (event)=>{
    const chatId:string=event.params.chatId;
    const snap=event.data;
    if (!snap) return;

    const chat=snap.data() as {
      members?:unknown;
      createdBy?:string;
      type?:string;
      name?:string;
    };

    const membersRaw=Array.isArray(chat.members)?chat.members:[];
    const members=membersRaw
      .map((member)=>typeof member==="string"?member.trim():"")
      .filter((member):member is string=>member.length>0);
    if (members.length===0) return;

    const creator=(typeof chat.createdBy==="string"?chat.createdBy.trim():"");
    const type=((typeof chat.type==="string"?chat.type:"direct").toLowerCase());
    const targets=creator?members.filter((email)=>email!==creator):members;
    if (targets.length===0) return;

    const db=admin.firestore();
    const usersCol=db.collection("users");
    const tokenDocs=await collectUserTokenDocs(targets, usersCol);
    const uniqueTokens=unique(tokenDocs.flatMap((item)=>item.tokens));
    if (uniqueTokens.length===0) return;

    let creatorName=creator.split("@")[0]||"Alguien";
    if (creator) {
      try {
        const creatorSnap=await usersCol.doc(creator).get();
        const name=creatorSnap.get("name") as string|undefined;
        if (name) creatorName=name;
      } catch {
        // ignoramos errores al cargar el creador
      }
    }

    const data:{[k:string]:string}={
      event: "chat_created",
      chatId,
      destType: type==="group"?"group":"private",
      creator,
      creatorName,
    };

    if (type==="group") {
      const groupName=(
        typeof chat.name==="string"?chat.name.trim():""
      )||"Nuevo grupo";
      data["groupName"]=groupName;
      data["peer"]=chatId;
    } else {
      data["peer"]=creator||chatId;
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

      await removeInvalidTokens(tokenDocs, toDelete);
    } catch (e) {
      console.error("sendMulticast error", e);
    }
  }
);
