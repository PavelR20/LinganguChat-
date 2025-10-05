package com.example.lingaguchat.ui.chat

sealed class ChatDestination(open val chatId: String, open val members: List<String>) {
    data class Direct(
        override val chatId: String,
        val peerEmail: String,
        override val members: List<String>
    ) : ChatDestination(chatId, members)

    data class Group(
        override val chatId: String,
        val name: String,
        override val members: List<String>
    ) : ChatDestination(chatId, members)
}
