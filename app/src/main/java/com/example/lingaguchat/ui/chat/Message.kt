package com.example.lingaguchat.ui.chat

data class Message(
    val id: String = "",
    val sender: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
