package com.example.lingaguchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.Timestamp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUserEmail: String,
    onOpenChat: (ChatDestination) -> Unit,
    onSignOut: () -> Unit,
    snackbarHostState: SnackbarHostState,
    chatsViewModel: ChatsViewModel = viewModel()
) {
    val chats by chatsViewModel.chats.collectAsStateWithLifecycle()
    val contacts by chatsViewModel.contacts.collectAsStateWithLifecycle()
    val isLoadingChats by chatsViewModel.isLoadingChats.collectAsStateWithLifecycle()
    val isLoadingContacts by chatsViewModel.isLoadingContacts.collectAsStateWithLifecycle()
    val isCreatingGroup by chatsViewModel.isCreatingGroup.collectAsStateWithLifecycle()
    val errorMessage by chatsViewModel.errorMessage.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserEmail) {
        chatsViewModel.observe(currentUserEmail)
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage!!)
            chatsViewModel.clearError()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var createGroupError by remember { mutableStateOf<String?>(null) }

    val contactsDirectory = remember(contacts) {
        contacts.associateBy { it.email }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "LinganguChat", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = {
                    createGroupError = null
                    showCreateGroup = true
                }) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Nuevo grupo")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Chats", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Contactos", modifier = Modifier.padding(16.dp))
                }
            }

            when (selectedTab) {
                0 -> ChatsTab(
                    currentUserEmail = currentUserEmail,
                    chats = chats,
                    contactsDirectory = contactsDirectory,
                    isLoading = isLoadingChats,
                    onChatClick = { summary ->
                        val destination = summary.toDestination(currentUserEmail)
                        onOpenChat(destination)
                    }
                )

                1 -> ContactsTab(
                    currentUserEmail = currentUserEmail,
                    contacts = contacts,
                    isLoading = isLoadingContacts,
                    onStartChat = { email ->
                        scope.launch {
                            createGroupError = null
                            try {
                                val chat = chatsViewModel.ensureDirectChat(currentUserEmail, email)
                                onOpenChat(chat.toDestination(currentUserEmail, email))
                            } catch (_: Exception) {
                                // error already surfaced via snackbar
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreateGroup) {
        val availableContacts = contacts.filter { it.email != currentUserEmail }
        NewGroupDialog(
            contacts = availableContacts,
            isProcessing = isCreatingGroup,
            errorMessage = createGroupError,
            onDismiss = {
                if (!isCreatingGroup) {
                    showCreateGroup = false
                    createGroupError = null
                }
            },
            onCreate = { name, selectedMembers ->
                val distinctMembers = selectedMembers.distinct()
                if (distinctMembers.size + 1 < 3) {
                    createGroupError = "Selecciona al menos 2 contactos además de ti"
                    return@NewGroupDialog
                }
                scope.launch {
                    createGroupError = null
                    try {
                        val chat = chatsViewModel.createGroupChat(name, distinctMembers, currentUserEmail)
                        showCreateGroup = false
                        onOpenChat(ChatDestination.Group(chat.id, chat.name ?: name.trim(), chat.members))
                    } catch (e: IllegalArgumentException) {
                        createGroupError = e.message
                    } catch (_: Exception) {
                        // error shown via snackbar
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatsTab(
    currentUserEmail: String,
    chats: List<ChatSummary>,
    contactsDirectory: Map<String, UserProfile>,
    isLoading: Boolean,
    onChatClick: (ChatSummary) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Cargando conversaciones…")
        }
        return
    }

    if (chats.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Todavía no tienes chats. ¡Inicia uno desde la pestaña de contactos!")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(chats, key = { it.id }) { summary ->
            val title = summary.displayName(currentUserEmail, contactsDirectory)
            val preview = summary.previewText(currentUserEmail, contactsDirectory)
            val timestampText = summary.lastTimestamp?.let { formatChatTimestamp(it.toDate()) }
            ChatListItem(
                title = title,
                subtitle = preview,
                timestamp = timestampText,
                onClick = { onChatClick(summary) }
            )
            Divider()
        }
    }
}

@Composable
private fun ContactsTab(
    currentUserEmail: String,
    contacts: List<UserProfile>,
    isLoading: Boolean,
    onStartChat: (String) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Cargando contactos…")
        }
        return
    }
    val otherContacts = contacts.filter { it.email != currentUserEmail }
    if (otherContacts.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aún no hay otros usuarios registrados")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(otherContacts, key = { it.email }) { profile ->
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        OnlineIndicator(isOnline = profile.isOnline)
                    }
                },
                supportingContent = {
                    val status = if (profile.isOnline) {
                        "En línea"
                    } else {
                        profile.lastSeen?.let { formatLastSeen(it) } ?: "Desconectado"
                    }
                    Text(status)
                },
                modifier = Modifier
                    .clickable { onStartChat(profile.email) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Divider()
        }
    }
}

@Composable
private fun ChatListItem(
    title: String,
    subtitle: String?,
    timestamp: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = remember(title) { title.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" } }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        timestamp?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OnlineIndicator(isOnline: Boolean) {
    val color = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun NewGroupDialog(
    contacts: List<UserProfile>,
    isProcessing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo grupo") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    label = { Text("Nombre del grupo") }
                )
                Spacer(Modifier.height(12.dp))
                Text("Selecciona integrantes", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (contacts.isEmpty()) {
                    Text("No hay contactos disponibles todavía")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(contacts, key = { it.email }) { profile ->
                            val checked = selectedMembers.contains(profile.email)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isProcessing) {
                                        selectedMembers = if (checked) {
                                            selectedMembers - profile.email
                                        } else {
                                            selectedMembers + profile.email
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (checked) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) {
                                        Text("✓", color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                                    Text(profile.email, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, selectedMembers.toList()) }, enabled = !isProcessing) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancelar") }
        }
    )
}

private fun ChatSummary.displayName(
    currentUserEmail: String,
    contactsDirectory: Map<String, UserProfile>
): String {
    return when (type) {
        ChatType.GROUP -> name ?: "Grupo"
        ChatType.DIRECT -> {
            val other = members.firstOrNull { it != currentUserEmail } ?: currentUserEmail
            contactsDirectory[other]?.name ?: other.substringBefore("@")
        }
    }
}

private fun ChatSummary.previewText(
    currentUserEmail: String,
    contactsDirectory: Map<String, UserProfile>
): String? {
    val senderName = lastMessageSender?.let { email ->
        if (email == currentUserEmail) "Tú" else contactsDirectory[email]?.name ?: email.substringBefore("@")
    }
    val baseMessage = when (lastMessageType) {
        MessageType.IMAGE -> lastMessage ?: "📷 Imagen"
        MessageType.TEXT, null -> lastMessage
    }
    return baseMessage?.let { message ->
        if (!senderName.isNullOrBlank()) "$senderName: $message" else message
    }
}

private fun ChatSummary.toDestination(currentUserEmail: String): ChatDestination {
    return when (type) {
        ChatType.GROUP -> ChatDestination.Group(id, name ?: "Grupo", members)
        ChatType.DIRECT -> {
            val other = members.firstOrNull { it != currentUserEmail } ?: currentUserEmail
            ChatDestination.Direct(id, other, members)
        }
    }
}

private fun ChatSummary.toDestination(
    currentUserEmail: String,
    otherEmail: String
): ChatDestination {
    return when (type) {
        ChatType.GROUP -> ChatDestination.Group(id, name ?: "Grupo", members)
        ChatType.DIRECT -> ChatDestination.Direct(id, otherEmail, members.ifEmpty { listOf(currentUserEmail, otherEmail) })
    }
}

private fun formatChatTimestamp(date: Date): String {
    val now = System.currentTimeMillis()
    val sameDay = isSameDay(date.time, now)
    val formatter: DateFormat = if (sameDay) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault())
    }
    return formatter.format(date)
}

fun formatLastSeen(lastSeen: Timestamp): String {
    val date = lastSeen.toDate()
    val now = System.currentTimeMillis()
    val formatter: DateFormat = if (isSameDay(date.time, now)) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    } else {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    }
    return "Últ. vez ${formatter.format(date)}"
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}
