package com.example.lingaguchat.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val chatsCollection = firestore.collection("chats")

    private val pageSize = 50L

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage: StateFlow<Boolean> = _isUploadingImage.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var messagesListener: ListenerRegistration? = null
    private var oldestSnapshot: DocumentSnapshot? = null
    private var currentChatId: String? = null

    fun subscribeMessages(chatId: String): Flow<List<Message>> {
        if (currentChatId == chatId && messagesListener != null) {
            return messages
        }
        stopListening()
        currentChatId = chatId
        _messages.value = emptyList()
        _hasMore.value = true
        oldestSnapshot = null
        val messagesRef = chatsCollection.document(chatId).collection("messages")
        val query = messagesRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .orderBy("localTimestamp", Query.Direction.ASCENDING)
            .limitToLast(pageSize.toInt().toLong())

        messagesListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _errorMessage.value = error.localizedMessage
                return@addSnapshotListener
            }
            if (snapshot == null) {
                _messages.value = emptyList()
                oldestSnapshot = null
                return@addSnapshotListener
            }
            val docs = snapshot.documents
            oldestSnapshot = docs.firstOrNull()
            _messages.value = docs.map { it.toMessage(chatId) }
        }

        return messages
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun stopListening() {
        messagesListener?.remove()
        messagesListener = null
        currentChatId = null
        oldestSnapshot = null
        _messages.value = emptyList()
        _hasMore.value = true
        _isLoadingMore.value = false
    }

    suspend fun loadPrevious(chatId: String) {
        if (chatId != currentChatId) return
        val anchor = oldestSnapshot ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        try {
            val messagesRef = chatsCollection.document(chatId).collection("messages")
            val query = messagesRef
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .orderBy("localTimestamp", Query.Direction.ASCENDING)
                .endBefore(anchor)
                .limitToLast(pageSize.toInt().toLong())
            val snapshot = query.get().await()
            if (snapshot.isEmpty) {
                _hasMore.value = false
            } else {
                val docs = snapshot.documents
                oldestSnapshot = docs.firstOrNull()
                val older = docs.map { it.toMessage(chatId) }
                _messages.update { older + it }
            }
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
        } finally {
            _isLoadingMore.value = false
        }
    }

    suspend fun sendText(chatId: String, text: String, sender: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val chatRef = chatsCollection.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val now = System.currentTimeMillis()
        try {
            val payload = mutableMapOf<String, Any>(
                "sender" to sender,
                "localTimestamp" to now,
                "readBy" to listOf(sender),
                "timestamp" to FieldValue.serverTimestamp()
            )
            payload["text"] = MessageCipher.encrypt(trimmed)
            messageRef.set(payload).await()
            chatRef.set(
                mapOf(
                    "lastMessage" to trimmed,
                    "lastMessageSender" to sender,
                    "lastMessageType" to MessageType.TEXT.name.lowercase(),
                    "lastTimestamp" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
            throw e
        }
    }

    suspend fun sendImage(chatId: String, uri: Uri, sender: String, caption: String? = null) {
        val chatRef = chatsCollection.document(chatId)
        val imagesFolder = storage.reference.child("chats/$chatId/images")
        val fileName = "${UUID.randomUUID()}.jpg"
        val imageRef = imagesFolder.child(fileName)
        _isUploadingImage.value = true
        try {
            imageRef.putFile(uri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            val now = System.currentTimeMillis()
            val messageRef = chatRef.collection("messages").document()
            val payload = mutableMapOf<String, Any>(
                "sender" to sender,
                "imageUrl" to downloadUrl.toString(),
                "localTimestamp" to now,
                "readBy" to listOf(sender),
                "timestamp" to FieldValue.serverTimestamp()
            )
            val captionText = caption?.trim().orEmpty()
            if (captionText.isNotEmpty()) {
                payload["text"] = MessageCipher.encrypt(captionText)
            }
            messageRef.set(payload).await()
            val preview = if (captionText.isNotEmpty()) captionText else "📷 Imagen"
            chatRef.set(
                mapOf(
                    "lastMessage" to preview,
                    "lastMessageSender" to sender,
                    "lastMessageType" to MessageType.IMAGE.name.lowercase(),
                    "lastTimestamp" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
            throw e
        } finally {
            _isUploadingImage.value = false
        }
    }

    fun markMessagesAsRead(chatId: String, reader: String) {
        if (chatId != currentChatId) return
        val pending = _messages.value.filter { reader !in it.readBy }
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val chatRef = chatsCollection.document(chatId)
            pending.forEach { message ->
                runCatching {
                    chatRef.collection("messages")
                        .document(message.id)
                        .update("readBy", FieldValue.arrayUnion(reader))
                        .await()
                }
            }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private fun DocumentSnapshot.toMessage(chatId: String): Message {
        val encryptedText = getString("text").orEmpty()
        val decrypted = encryptedText.takeIf { it.isNotBlank() }?.let { MessageCipher.decrypt(it) }
        val imageUrl = getString("imageUrl")
        val type = if (!imageUrl.isNullOrBlank()) MessageType.IMAGE else MessageType.TEXT
        val readBy = get("readBy") as? List<String> ?: emptyList()
        val localTs = getLong("localTimestamp") ?: 0L
        val ts = getTimestamp("timestamp")
        return Message(
            id = id,
            chatId = chatId,
            sender = getString("sender").orEmpty(),
            text = decrypted,
            imageUrl = imageUrl,
            type = type,
            localTimestamp = localTs,
            readBy = readBy,
            timestamp = ts
        )
    }
}
