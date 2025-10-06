package com.example.lingaguchat.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.lingaguchat.MainActivity
import com.example.lingaguchat.R
import com.example.lingaguchat.ui.chat.MessageCipher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore

class AppMessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "messages_channel_id"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        FcmTokenManager.storeToken(email, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val event = data["event"] ?: "message"
        when (event) {
            "chat_created" -> handleChatCreatedEvent(data)
            else -> handleMessageEvent(data)
        }
    }

    private fun handleMessageEvent(data: Map<String, String>) {
        val messageId = data["messageId"] ?: return
        val chatId = data["chatId"] ?: return
        val destType = data["destType"] ?: "private"
        val peer = data["peer"] ?: return
        val groupName = data["groupName"]

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) return@addOnSuccessListener
                val encryptedText = snap.getString("text").orEmpty()
                val imageUrl = snap.getString("imageUrl")
                val decrypted = MessageCipher.decrypt(encryptedText).trim()

                val title = when (destType) {
                    "group" -> groupName ?: "Nuevo mensaje"
                    else -> data["senderName"] ?: peer.substringBefore("@")
                }

                val preview = if (!imageUrl.isNullOrBlank()) {
                    if (decrypted.isNotEmpty()) "📷 $decrypted" else "📷 Imagen"
                } else {
                    decrypted.ifBlank { "Nuevo mensaje" }
                }

                showNotification(title, preview, destType, peer, chatId, messageId, groupName)
            }
    }

    private fun handleChatCreatedEvent(data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val destType = data["destType"] ?: "private"
        val peer = data["peer"] ?: chatId
        val creator = data["creator"]
        val creatorName = data["creatorName"].takeUnless { it.isNullOrBlank() }
            ?: creator?.substringBefore("@")
            ?: "Nuevo chat"
        val groupName = data["groupName"].takeUnless { it.isNullOrBlank() }

        val title = if (destType == "group") {
            groupName ?: "Nuevo grupo"
        } else {
            creatorName
        }

        val body = if (destType == "group") {
            val groupLabel = groupName ?: "nuevo grupo"
            "$creatorName creó el grupo $groupLabel"
        } else {
            "$creatorName inició un chat contigo"
        }

        showNotification(title, body, destType, peer, chatId, null, groupName)
    }

    private fun showNotification(
        title: String,
        body: String,
        destType: String,
        peer: String,
        chatId: String,
        messageId: String?,
        groupName: String?
    ) {
        createChannelIfNeeded()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openFromNotification", true)
            putExtra("destType", destType) // "private" | "group"
            putExtra("peer", peer)         // email o groupId
            putExtra("chatId", chatId)
            groupName?.let { putExtra("groupName", it) }
            messageId?.let { putExtra("messageId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(sound)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de nuevos mensajes"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
