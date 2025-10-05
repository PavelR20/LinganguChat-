package com.example.lingaguchat.ui.chat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

enum class MessageType { TEXT, IMAGE }

data class Message(
    val id: String = "",
    val sender: String = "",
    val receiver: String = "",
    val groupId: String? = null,
    val participants: List<String> = emptyList(),
    val text: String = "",
    val imageUrl: String? = null,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    @ServerTimestamp val serverTime: Timestamp? = null
)
