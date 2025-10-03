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

    private val messagesCollection = db.collection("messages")
    private val storageFolder = storage.reference.child("chat_images")

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private var sentReg: ListenerRegistration? = null
    private var recvReg: ListenerRegistration? = null
    private var groupReg: ListenerRegistration? = null
    private var sentList: List<Message> = emptyList()
    private var recvList: List<Message> = emptyList()

    fun listenPrivateMessages(currentUser: String, otherUser: String) {
        groupReg?.remove(); groupReg = null
        sentReg?.remove(); recvReg?.remove()
        sentList = emptyList(); recvList = emptyList()
        _messages.value = emptyList()

        sentReg = messagesCollection
            .whereEqualTo("sender", currentUser)
            .whereEqualTo("receiver", otherUser)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 sent query error: ${e.message}")
                    return@addSnapshotListener
                }
                sentList = snap?.toObjects(Message::class.java)
                    ?.map(::normalizeMessage)
                    .orEmpty()
                publishMerged()
            }

        recvReg = messagesCollection
            .whereEqualTo("sender", otherUser)
            .whereEqualTo("receiver", currentUser)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 recv query error: ${e.message}")
                    return@addSnapshotListener
                }
                recvList = snap?.toObjects(Message::class.java)
                    ?.map(::normalizeMessage)
                    .orEmpty()
                publishMerged()
            }
    }

    fun listenGroupMessages(groupId: String) {
        sentReg?.remove(); recvReg?.remove()
        sentReg = null; recvReg = null
        sentList = emptyList(); recvList = emptyList()
        _messages.value = emptyList()

        groupReg?.remove()
        groupReg = messagesCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 group query error: ${e.message}")
                    return@addSnapshotListener
                }
                val groupMessages = snap?.toObjects(Message::class.java)
                    ?.map(::normalizeMessage)
                    .orEmpty()
                _messages.value = groupMessages.sortedBy { it.timestamp }
            }
    }

    private fun publishMerged() {
        _messages.value = (sentList + recvList)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    fun sendDirectMessage(sender: String, receiver: String, text: String) {
        if (text.isBlank()) return
        val docRef = messagesCollection.document()
        val newMessage = Message(
            id = docRef.id,
            sender = sender,
            receiver = receiver,
            groupId = null,
            participants = listOf(sender, receiver).distinct(),
            text = MessageCipher.encrypt(text),
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis()
        )
        docRef.set(newMessage)
    }

    fun sendGroupMessage(
        sender: String,
        groupId: String,
        participants: List<String>,
        text: String
    ) {
        if (text.isBlank()) return
        val docRef = messagesCollection.document()
        val message = Message(
            id = docRef.id,
            sender = sender,
            receiver = "",
            groupId = groupId,
            participants = (participants + sender).distinct(),
            text = MessageCipher.encrypt(text),
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis()
        )
        docRef.set(message)
    }

    fun sendDirectImageMessage(
        sender: String,
        receiver: String,
        imageUri: Uri,
        caption: String = "",
        onResult: (Boolean) -> Unit = {},
    ) {
        uploadAndSendImage(
            sender = sender,
            receiver = receiver,
            groupId = null,
            participants = listOf(sender, receiver).distinct(),
            imageUri = imageUri,
            caption = caption,
            onResult = onResult
        )
    }

    fun sendGroupImageMessage(
        sender: String,
        groupId: String,
        participants: List<String>,
        imageUri: Uri,
        caption: String = "",
        onResult: (Boolean) -> Unit = {},
    ) {
        uploadAndSendImage(
            sender = sender,
            receiver = "",
            groupId = groupId,
            participants = (participants + sender).distinct(),
            imageUri = imageUri,
            caption = caption,
            onResult = onResult
        )
    }

    private fun uploadAndSendImage(
        sender: String,
        receiver: String?,
        groupId: String?,
        participants: List<String>,
        imageUri: Uri,
        caption: String,
        onResult: (Boolean) -> Unit
    ) {
        val fileName = "${UUID.randomUUID()}_${imageUri.lastPathSegment ?: "image"}"
        val imageRef = storageFolder.child(fileName)

        imageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val docRef = messagesCollection.document()
                val imageMessage = Message(
                    id = docRef.id,
                    sender = sender,
                    receiver = receiver ?: "",
                    groupId = groupId,
                    participants = participants,
                    text = MessageCipher.encrypt(caption),
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

    private fun normalizeMessage(message: Message): Message {
        val participants = if (message.participants.isNotEmpty()) {
            message.participants
        } else {
            listOfNotNull(
                message.sender.takeIf { it.isNotBlank() },
                message.receiver.takeIf { it.isNotBlank() }
            )
        }
        return message.copy(
            text = MessageCipher.decrypt(message.text),
            participants = participants.distinct()
        )
    }

    override fun onCleared() {
        sentReg?.remove(); recvReg?.remove(); groupReg?.remove()
        super.onCleared()
    }
}
