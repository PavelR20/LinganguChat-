package com.example.lingaguchat.ui.chat

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
    val timestamp: Long = System.currentTimeMillis()
)
