package com.example.lingaguchat.ui.chat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

enum class MessageType { TEXT, IMAGE }

enum class ChatType { DIRECT, GROUP }

data class Message(
    val id: String = "",
    val chatId: String = "",
    val sender: String = "",
    val text: String? = null,
    val imageUrl: String? = null,
    val type: MessageType = MessageType.TEXT,
    val localTimestamp: Long = System.currentTimeMillis(),
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrBlank()
    val hasText: Boolean get() = !text.isNullOrBlank()
    val effectiveTimeMillis: Long
        get() = timestamp?.toDate()?.time ?: localTimestamp
}

data class ChatSummary(
    val id: String = "",
    val type: ChatType = ChatType.DIRECT,
    val name: String? = null,
    val members: List<String> = emptyList(),
    val lastMessage: String? = null,
    val lastMessageSender: String? = null,
    val lastMessageType: MessageType? = null,
    @ServerTimestamp val lastTimestamp: Timestamp? = null
)

data class UserProfile(
    val email: String = "",
    val name: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null
) {
    // 🔹 Conversión opcional para usarlo como Long
    val lastSeenMillis: Long?
        get() = lastSeen?.toDate()?.time
}
