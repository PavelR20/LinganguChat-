package com.example.lingaguchat.ui.chat

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class ChatsViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val chatsCollection = firestore.collection("chats")
    private val usersCollection = firestore.collection("users")

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _contacts = MutableStateFlow<List<UserProfile>>(emptyList())
    val contacts: StateFlow<List<UserProfile>> = _contacts.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isCreatingGroup = MutableStateFlow(false)
    val isCreatingGroup: StateFlow<Boolean> = _isCreatingGroup.asStateFlow()

    private var chatsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    fun observe(currentUser: String) {
        _isLoadingChats.value = true
        chatsListener?.remove()
        chatsListener = chatsCollection
            .whereArrayContains("members", currentUser)
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                _isLoadingChats.value = false
                if (error != null) {
                    _errorMessage.value = error.localizedMessage
                    _chats.value = emptyList()
                    return@addSnapshotListener
                }
                val chatItems = snapshot?.documents?.map { it.toChatSummary() }.orEmpty()
                _chats.value = chatItems
            }

        _isLoadingContacts.value = true
        usersListener?.remove()
        usersListener = usersCollection
            .addSnapshotListener { snapshot, error ->
                _isLoadingContacts.value = false
                if (error != null) {
                    _errorMessage.value = error.localizedMessage
                    _contacts.value = emptyList()
                    return@addSnapshotListener
                }
                val profiles = snapshot?.documents?.mapNotNull { doc ->
                    val email = doc.getString("email") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: email.substringBefore("@")
                    val isOnline = doc.getBoolean("online") ?: false
                    val lastSeen = doc.getLong("lastSeen")
                    UserProfile(email = email, name = name, isOnline = isOnline, lastSeen = lastSeen)
                }.orEmpty()
                _contacts.value = profiles
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    suspend fun ensureDirectChat(currentUser: String, otherUser: String): ChatSummary {
        val normalizedCurrent = currentUser.trim()
        val normalizedOther = otherUser.trim()
        require(normalizedCurrent.isNotBlank() && normalizedOther.isNotBlank())
        val orderedMembers = listOf(normalizedCurrent, normalizedOther)
            .map { it.trim() }
            .sortedBy { it.lowercase(Locale.ROOT) }
        return try {
            val key = buildDirectKey(normalizedCurrent, normalizedOther)
            val chatId = buildDirectChatId(key)
            val chatRef = chatsCollection.document(chatId)
            val snapshot = chatRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "type" to ChatType.DIRECT.name.lowercase(Locale.ROOT),
                    "members" to orderedMembers,
                    "directKey" to key,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastTimestamp" to FieldValue.serverTimestamp()
                )
                chatRef.set(payload, SetOptions.merge()).await()
            } else {
                val existingMembers = snapshot.get("members") as? List<*>
                val existingMemberStrings = existingMembers
                    ?.mapNotNull { it as? String }
                    ?.map { it.trim() }
                    ?: emptyList()
                val existingLower = existingMemberStrings.map { it.lowercase(Locale.ROOT) }
                val expectedLower = orderedMembers.map { it.lowercase(Locale.ROOT) }
                if (existingLower != expectedLower || existingMemberStrings != orderedMembers) {
                    chatRef.set(mapOf("members" to orderedMembers), SetOptions.merge()).await()
                }
            }
            chatRef.get().await().toChatSummary()
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
            throw e
        }
    }

    suspend fun createGroupChat(name: String, members: List<String>, currentUser: String): ChatSummary {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            val error = "El nombre del grupo es obligatorio"
            _errorMessage.value = error
            throw IllegalArgumentException(error)
        }
        val distinctMembers = (members + currentUser)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (distinctMembers.size < 3) {
            val error = "El grupo debe tener al menos 3 integrantes incluyendo al creador"
            _errorMessage.value = error
            throw IllegalArgumentException(error)
        }
        _isCreatingGroup.value = true
        return try {
            val chatRef = chatsCollection.document()
            val payload = mapOf(
                "type" to ChatType.GROUP.name.lowercase(Locale.ROOT),
                "name" to trimmedName,
                "members" to distinctMembers,
                "createdBy" to currentUser,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastTimestamp" to FieldValue.serverTimestamp()
            )
            chatRef.set(payload, SetOptions.merge()).await()
            chatRef.get().await().toChatSummary()
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
            throw e
        } finally {
            _isCreatingGroup.value = false
        }
    }

    override fun onCleared() {
        chatsListener?.remove()
        usersListener?.remove()
        super.onCleared()
    }

    private fun buildDirectKey(a: String, b: String): String {
        return listOf(a.lowercase(Locale.ROOT), b.lowercase(Locale.ROOT))
            .sorted()
            .joinToString("|")
    }

    private fun buildDirectChatId(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            String.format("%02x", byte)
        }.take(40)
    }

    private fun DocumentSnapshot.toChatSummary(): ChatSummary {
        val typeRaw = getString("type")?.lowercase(Locale.ROOT)
        val chatType = when (typeRaw) {
            "group" -> ChatType.GROUP
            else -> ChatType.DIRECT
        }
        val members = (get("members") as? List<*>)
            ?.mapNotNull { (it as? String)?.trim() }
            ?: emptyList()
        val lastTypeRaw = getString("lastMessageType")?.lowercase(Locale.ROOT)
        val lastType = when (lastTypeRaw) {
            "image" -> MessageType.IMAGE
            "text" -> MessageType.TEXT
            else -> null
        }
        return ChatSummary(
            id = id,
            type = chatType,
            name = getString("name"),
            members = members,
            lastMessage = getString("lastMessage"),
            lastMessageSender = getString("lastMessageSender"),
            lastMessageType = lastType,
            lastTimestamp = getTimestamp("lastTimestamp")
        )
    }
}
