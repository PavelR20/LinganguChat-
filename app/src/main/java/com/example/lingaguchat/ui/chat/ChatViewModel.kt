package com.example.lingaguchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    init {
        listenMessages()
    }

    private fun listenMessages() {
        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val msgs = snapshot.toObjects(Message::class.java)
                    _messages.value = msgs
                }
            }
    }

    fun sendMessage(sender: String, text: String) {
        val newMessage = Message(
            id = db.collection("messages").document().id,
            sender = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        db.collection("messages").document(newMessage.id).set(newMessage)
    }
}
