package com.example.lingaguchat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    destination: ChatDestination,
    onBack: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messagesFlow = remember(destination.chatId) { chatViewModel.subscribeMessages(destination.chatId) }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isUploadingImage by chatViewModel.isUploadingImage.collectAsStateWithLifecycle()
    val isLoadingMore by chatViewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by chatViewModel.hasMore.collectAsStateWithLifecycle()
    val errorMessage by chatViewModel.errorMessage.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage!!)
            chatViewModel.clearError()
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            scope.launch {
                chatViewModel.markMessagesAsRead(destination.chatId, currentUserEmail)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) -> index == 0 && offset == 0 }
            .distinctUntilChanged()
            .collectLatest { atTop ->
                if (atTop && hasMore && !isLoadingMore) {
                    chatViewModel.loadPrevious(destination.chatId)
                }
            }
    }

    DisposableEffect(destination.chatId) {
        onDispose { chatViewModel.stopListening() }
    }

    LaunchedEffect(destination) {
        text = ""
        selectedImageUri = null
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }

    val firestore = remember { FirebaseFirestore.getInstance() }
    var title by remember { mutableStateOf("Chat") }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var members by remember { mutableStateOf(destination.members) }

    DisposableEffect(destination) {
        val usersCollection = firestore.collection("users")
        val chatDoc = firestore.collection("chats").document(destination.chatId)
        var infoListener: ListenerRegistration? = null
        val memberListeners = mutableMapOf<String, ListenerRegistration>()

        fun attachMemberListeners(target: List<String>) {
            val normalized = target.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val toRemove = memberListeners.keys - normalized.toSet()
            toRemove.forEach { key ->
                memberListeners.remove(key)?.remove()
            }
            val toAdd = normalized.filter { !memberListeners.containsKey(it) }
            toAdd.forEach { email ->
                val listener = usersCollection.document(email)
                    .addSnapshotListener { snapshot, _ ->
                        val name = snapshot?.getString("name") ?: email.substringBefore("@")
                        memberNames = memberNames.toMutableMap().apply { put(email, name) }
                    }
                memberListeners[email] = listener
            }
        }

        memberNames = emptyMap()
        members = destination.members
        subtitle = null

        when (destination) {
            is ChatDestination.Direct -> {
                title = destination.peerEmail.substringBefore("@")
                members = listOf(destination.peerEmail, currentUserEmail)
                infoListener = usersCollection.document(destination.peerEmail)
                    .addSnapshotListener { snapshot, _ ->
                        val name = snapshot?.getString("name") ?: destination.peerEmail.substringBefore("@")
                        title = name
                        val isOnline = snapshot?.getBoolean("online") ?: false
                        val lastSeen = snapshot?.getLong("lastSeen")
                        subtitle = if (isOnline) "En línea" else lastSeen?.let { formatLastSeen(it) }
                        memberNames = memberNames.toMutableMap().apply {
                            put(destination.peerEmail, name)
                            put(currentUserEmail, currentUserEmail.substringBefore("@"))
                        }
                    }
                attachMemberListeners(listOf(destination.peerEmail, currentUserEmail))
            }

            is ChatDestination.Group -> {
                infoListener = chatDoc.addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        title = snapshot.getString("name") ?: destination.chatId.take(8)
                        val chatMembers = snapshot.get("members") as? List<String>
                        if (!chatMembers.isNullOrEmpty()) {
                            val allMembers = (chatMembers + currentUserEmail).distinct()
                            members = allMembers
                            attachMemberListeners(allMembers)
                        }
                    }
                }
            }
        }

        onDispose {
            infoListener?.remove()
            memberListeners.values.forEach { it.remove() }
        }
    }

    val messageItems = remember(messages) { buildMessageItems(messages) }

    LaunchedEffect(messageItems) {
        if (messageItems.isNotEmpty() && listState.firstVisibleItemIndex >= messageItems.size - 4) {
            listState.animateScrollToItem(messageItems.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        if (destination is ChatDestination.Group && members.isNotEmpty()) {
                            val names = members
                                .map { memberNames[it] ?: it.substringBefore("@") }
                                .joinToString(", ")
                            Text(names, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isUploadingImage) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState
            ) {
                if (isLoadingMore) {
                    item("loadingMore") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                items(messageItems, key = { it.key }) { item ->
                    when (item) {
                        is MessageListItem.DateHeader -> DateHeader(label = item.label)
                        is MessageListItem.ChatMessage -> {
                            val message = item.message
                            val isMe = message.sender == currentUserEmail
                            val senderName = memberNames[message.sender] ?: message.sender.substringBefore("@")
                            MessageBubble(
                                message = message,
                                senderName = senderName,
                                isCurrentUser = isMe
                            )
                        }
                    }
                }
                item("bottomSpacer") { Spacer(modifier = Modifier.height(12.dp)) }
            }

            AnimatedVisibility(visible = selectedImageUri != null) {
                selectedImageUri?.let { imageUri ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        tonalElevation = 4.dp,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Imagen seleccionada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar imagen")
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                text = text,
                onTextChange = { text = it },
                onPickImage = {
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSend = {
                    val trimmed = text.trim()
                    val imageUri = selectedImageUri
                    scope.launch {
                        when {
                            imageUri != null -> {
                                try {
                                    chatViewModel.sendImage(destination.chatId, imageUri, currentUserEmail, trimmed)
                                    text = ""
                                    selectedImageUri = null
                                } catch (_: Exception) {
                                    // error already handled via snackbar
                                }
                            }
                            trimmed.isNotEmpty() -> {
                                try {
                                    chatViewModel.sendText(destination.chatId, trimmed, currentUserEmail)
                                    text = ""
                                } catch (_: Exception) {
                                    // error already handled via snackbar
                                }
                            }
                        }
                    }
                },
                sendEnabled = text.isNotBlank() || selectedImageUri != null,
                isSendingImage = isUploadingImage
            )
        }
    }
}

private sealed class MessageListItem(val key: String) {
    data class DateHeader(val id: String, val label: String) : MessageListItem("header_$id")
    data class ChatMessage(val message: Message) : MessageListItem(message.id.ifEmpty { UUID.randomUUID().toString() })
}

private fun buildMessageItems(messages: List<Message>): List<MessageListItem> {
    if (messages.isEmpty()) return emptyList()
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val items = mutableListOf<MessageListItem>()
    var lastDateKey: String? = null
    messages.forEach { message ->
        val date = Date(message.effectiveTimeMillis)
        val key = dateFormatter.format(date)
        if (key != lastDateKey) {
            items += MessageListItem.DateHeader(key, formatDateChip(date.time))
            lastDateKey = key
        }
        items += MessageListItem.ChatMessage(message)
    }
    return items
}

@Composable
private fun DateHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    senderName: String,
    isCurrentUser: Boolean
) {
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = alignment
    ) {
        Text(
            senderName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.hasImage) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Imagen",
                        modifier = Modifier
                            .size(width = 220.dp, height = 220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (message.hasText) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message.text!!, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (message.hasText) {
                    Text(message.text!!, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            formatMessageTime(message.effectiveTimeMillis),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    isSendingImage: Boolean
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPickImage) {
                Icon(Icons.Default.Image, contentDescription = "Seleccionar imagen")
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 1.dp
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (text.isBlank()) {
                            Text("Escribe un mensaje…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
            }
            TextButton(onClick = onSend, enabled = sendEnabled && !isSendingImage) {
                if (isSendingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

private fun formatDateChip(time: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(time, now) -> "Hoy"
        isSameDay(time, now - ONE_DAY) -> "Ayer"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
    }
}

private fun formatMessageTime(time: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(time))
}

private fun formatLastSeen(lastSeen: Long): String {
    val formatter: DateFormat = if (isSameDay(lastSeen, System.currentTimeMillis())) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    } else {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    }
    return "Últ. vez ${formatter.format(Date(lastSeen))}"
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

private const val ONE_DAY = 24 * 60 * 60 * 1000L
