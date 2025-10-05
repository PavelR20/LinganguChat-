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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore

class AppMessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "messages_channel_id"

    override fun onNewToken(token: String) {
        // El AuthViewModel ya sube token, pero aquí puedes hacer un “best-effort”
        // si quisieras (obtener email actual y subirlo).
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val messageId = data["messageId"] ?: return
        val chatId = data["chatId"] ?: return
        val destType = data["destType"] ?: "private"
        val peer = data["peer"] ?: return

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
                    "group" -> data["groupName"] ?: "Nuevo mensaje"
                    else -> data["senderName"] ?: peer.substringBefore("@")
                }

                val preview = if (!imageUrl.isNullOrBlank()) {
                    if (decrypted.isNotEmpty()) "📷 $decrypted" else "📷 Imagen"
                } else {
                    decrypted.ifBlank { "Nuevo mensaje" }
                }

                showNotification(title, preview, destType, peer, chatId, messageId)
            }
    }

    private fun showNotification(
        title: String,
        body: String,
        destType: String,
        peer: String,
        chatId: String,
        messageId: String
    ) {
        createChannelIfNeeded()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openFromNotification", true)
            putExtra("destType", destType) // "private" | "group"
            putExtra("peer", peer)         // email o groupId
            putExtra("chatId", chatId)
            putExtra("messageId", messageId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(sound)
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
