// ChatViewModel.kt
package com.example.lingaguchat.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private var sentReg: ListenerRegistration? = null
    private var recvReg: ListenerRegistration? = null
    private var sentList: List<Message> = emptyList()
    private var recvList: List<Message> = emptyList()

    fun listenPrivateMessages(currentUser: String, otherUser: String) {
        sentReg?.remove(); recvReg?.remove()
        sentList = emptyList(); recvList = emptyList()
        _messages.value = emptyList()

        sentReg = db.collection("messages")
            .whereEqualTo("sender", currentUser)
            .whereEqualTo("receiver", otherUser)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 sent query error: ${e.message}")
                    return@addSnapshotListener
                }
                sentList = snap?.toObjects(Message::class.java).orEmpty()
                publishMerged()
            }

        recvReg = db.collection("messages")
            .whereEqualTo("sender", otherUser)
            .whereEqualTo("receiver", currentUser)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 recv query error: ${e.message}")
                    return@addSnapshotListener
                }
                recvList = snap?.toObjects(Message::class.java).orEmpty()
                publishMerged()
            }
    }

    private fun publishMerged() {
        _messages.value = (sentList + recvList)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    fun sendMessage(sender: String, receiver: String, text: String) {
        val docRef = db.collection("messages").document()
        val newMessage = Message(
            id = docRef.id,
            sender = sender,
            receiver = receiver,
            text = text,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis()
        )
        docRef.set(newMessage)
    }

    fun sendImageMessage(
        sender: String,
        receiver: String,
        imageUri: Uri,
        caption: String = "",
        onResult: (Boolean) -> Unit = {}
    ) {
        val imageRef = storage.reference
            .child("chat_images/${UUID.randomUUID()}_${imageUri.lastPathSegment ?: "image"}")

        imageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val docRef = db.collection("messages").document()
                val imageMessage = Message(
                    id = docRef.id,
                    sender = sender,
                    receiver = receiver,
                    text = caption,
                    imageUrl = downloadUri.toString(),
                    type = MessageType.IMAGE,
                    timestamp = System.currentTimeMillis()
                )
                docRef.set(imageMessage)
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener {
                        println("🔥 image message send error: ${it.message}")
                        onResult(false)
                    }
            }
            .addOnFailureListener {
                println("🔥 image upload error: ${it.message}")
                onResult(false)
            }
    }

    override fun onCleared() {
        sentReg?.remove(); recvReg?.remove()
        super.onCleared()
    }
}
