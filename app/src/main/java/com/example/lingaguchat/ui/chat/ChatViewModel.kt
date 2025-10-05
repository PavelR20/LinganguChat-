package com.example.lingaguchat.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.google.firebase.firestore.FieldValue
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

    // ---------------------------
    // LECTURA: PRIVADOS (2 queries)
    // ---------------------------
    fun listenPrivateMessages(currentUser: String, otherUser: String) {
        // Limpia listeners de grupo
        groupReg?.remove(); groupReg = null

        // Reinicia estado
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

    // ---------------------------
    // LECTURA: GRUPOS (1 query ordenada por serverTime)
    // ---------------------------
    fun listenGroupMessages(groupId: String) {
        // Limpia listeners de privados
        sentReg?.remove(); recvReg?.remove()
        sentReg = null; recvReg = null
        sentList = emptyList(); recvList = emptyList()
        _messages.value = emptyList()

        groupReg?.remove()
        groupReg = messagesCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("serverTime") // entrega ya ordenado por servidor
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    println("🔥 group query error: ${e.message}")
                    return@addSnapshotListener
                }
                val groupMessages = snap?.toObjects(Message::class.java)
                    ?.map(::normalizeMessage)
                    .orEmpty()

                // Fallback adicional por si alguno no tiene serverTime aún
                _messages.value = groupMessages.sortedWith(
                    compareBy<Message> { it.serverTime?.toDate()?.time ?: it.timestamp }
                        .thenBy { it.id } // desempate estable
                )
            }
    }

    // Combina y ordena privados por tiempo de servidor (fallback a timestamp)
    private fun publishMerged() {
        _messages.value = (sentList + recvList)
            .distinctBy { it.id }
            .sortedWith(
                compareBy<Message> { it.serverTime?.toDate()?.time ?: it.timestamp }
                    .thenBy { it.id }
            )
    }

    // ---------------------------
    // ENVÍO: TEXTO DIRECTO
    // ---------------------------
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
            timestamp = System.currentTimeMillis(),
            serverTime = null // lo rellenará el servidor
        )
        docRef.set(newMessage)
            .continueWithTask { docRef.update("serverTime", FieldValue.serverTimestamp()) }
            .addOnFailureListener { println("🔥 sendDirectMessage error: ${it.message}") }
    }

    // ---------------------------
    // ENVÍO: TEXTO A GRUPO
    // ---------------------------
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
            timestamp = System.currentTimeMillis(),
            serverTime = null
        )
        docRef.set(message)
            .continueWithTask { docRef.update("serverTime", FieldValue.serverTimestamp()) }
            .addOnFailureListener { println("🔥 sendGroupMessage error: ${it.message}") }
    }

    // ---------------------------
    // ENVÍO: IMAGEN DIRECTO/GRUPO
    // ---------------------------
    fun sendDirectImageMessage(
        sender: String,
        receiver: String,
        contentResolver: ContentResolver,
        imageUri: Uri,
        caption: String = "",
        onResult: (Boolean) -> Unit = {},
    ) {
        uploadAndSendImage(
            sender = sender,
            receiver = receiver,
            groupId = null,
            participants = listOf(sender, receiver).distinct(),
            contentResolver = contentResolver,
            imageUri = imageUri,
            caption = caption,
            onResult = onResult
        )
    }

    fun sendGroupImageMessage(
        sender: String,
        groupId: String,
        participants: List<String>,
        contentResolver: ContentResolver,
        imageUri: Uri,
        caption: String = "",
        onResult: (Boolean) -> Unit = {},
    ) {
        uploadAndSendImage(
            sender = sender,
            receiver = "",
            groupId = groupId,
            participants = (participants + sender).distinct(),
            contentResolver = contentResolver,
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
        contentResolver: ContentResolver,
        imageUri: Uri,
        caption: String,
        onResult: (Boolean) -> Unit
    ) {
        val originalName = imageUri.lastPathSegment?.substringAfterLast('/') ?: "image"
        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "${UUID.randomUUID()}_${safeName.ifBlank { "image" }}"
        val imageRef = storageFolder.child(fileName)

        val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
        val inputStream = runCatching { contentResolver.openInputStream(imageUri) }
            .getOrNull()

        if (inputStream == null) {
            println("🔥 image input stream null for uri: $imageUri")
            onResult(false)
            return
        }

        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()

        val uploadTask = imageRef.putStream(inputStream, metadata)
        uploadTask.addOnCompleteListener {
            try {
                inputStream.close()
            } catch (_: IOException) {
            }
        }

        uploadTask
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
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
                    timestamp = System.currentTimeMillis(),
                    serverTime = null
                )
                docRef.set(imageMessage)
                    .continueWithTask { docRef.update("serverTime", FieldValue.serverTimestamp()) }
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

    // ---------------------------
    // Normalización + desencriptado
    // ---------------------------
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
