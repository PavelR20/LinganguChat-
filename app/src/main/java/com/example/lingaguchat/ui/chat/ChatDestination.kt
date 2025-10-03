package com.example.lingaguchat.ui.chat

sealed class ChatDestination {
    data class Private(val email: String) : ChatDestination()
    data class Group(
        val id: String,
        val name: String,
        val members: List<String>
    ) : ChatDestination()
}
