package com.example.lingaguchat.ui.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

data class UserItem(
    val email: String,
    val name: String,
    val isOnline: Boolean,
    val lastSeen: Long?
)

data class GroupItem(
    val id: String,
    val name: String,
    val members: List<String>
)

@Composable
fun DrawerContent(
    currentUserEmail: String,
    selectedChat: ChatDestination?,
    onChatSelected: (ChatDestination) -> Unit,
    onSignOut: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var accounts by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var loadingUsers by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var groups by remember { mutableStateOf<List<GroupItem>>(emptyList()) }
    var loadingGroups by remember { mutableStateOf(true) }
    var groupsError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(currentUserEmail) {
        val usersReg = db.collection("users")
            .addSnapshotListener { snapshot, e ->
                loadingUsers = false
                if (e != null) {
                    errorMsg = e.message
                    Log.e("DrawerContent", "Firestore users error", e)
                    accounts = emptyList()
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val email = doc.getString("email") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: email.substringBefore("@")
                    val isOnline = doc.getBoolean("online") ?: false
                    val lastSeen = doc.getLong("lastSeen")
                    UserItem(email, name, isOnline, lastSeen)
                }.orEmpty()
                accounts = list
            }

        val groupsReg = db.collection("groups")
            .whereArrayContains("members", currentUserEmail)
            .addSnapshotListener { snapshot, e ->
                loadingGroups = false
                if (e != null) {
                    groupsError = e.message
                    Log.e("DrawerContent", "Firestore groups error", e)
                    groups = emptyList()
                    return@addSnapshotListener
                }
                groups = snapshot?.documents?.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: "Grupo"
                    val members = doc.get("members") as? List<String> ?: emptyList()
                    GroupItem(id, name, members)
                }.orEmpty()
            }

        onDispose {
            usersReg?.remove()
            groupsReg?.remove()
        }
    }

    var showCreateGroup by remember { mutableStateOf(false) }
    var createGroupError by remember { mutableStateOf<String?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }

    if (showCreateGroup) {
        CreateGroupDialog(
            availableUsers = accounts.filter { it.email != currentUserEmail },
            isProcessing = creatingGroup,
            errorMessage = createGroupError,
            onDismiss = {
                if (!creatingGroup) {
                    showCreateGroup = false
                    createGroupError = null
                }
            },
            onCreate = { name, selectedMembers ->
                val trimmedName = name.trim()
                if (trimmedName.isBlank()) {
                    createGroupError = "Asigna un nombre al grupo"
                    return@CreateGroupDialog
                }
                if (selectedMembers.isEmpty()) {
                    createGroupError = "Selecciona al menos un integrante"
                    return@CreateGroupDialog
                }
                creatingGroup = true
                val payload = mapOf(
                    "name" to trimmedName,
                    "members" to (selectedMembers + currentUserEmail).distinct()
                )
                db.collection("groups")
                    .add(payload)
                    .addOnSuccessListener {
                        creatingGroup = false
                        createGroupError = null
                        showCreateGroup = false
                    }
                    .addOnFailureListener {
                        creatingGroup = false
                        createGroupError = it.message
                        Log.e("DrawerContent", "Create group error", it)
                    }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Conversaciones", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Text("Contactos", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))

        when {
            loadingUsers -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Cargando contactos…")
            }
            errorMsg != null -> {
                Text(
                    "Error al cargar: $errorMsg",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                val otherUsers = accounts.filter { it.email != currentUserEmail }
                if (otherUsers.isEmpty()) {
                    Text("No hay contactos registrados todavía")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        items(otherUsers) { user ->
                            val isSelected = selectedChat is ChatDestination.Private && selectedChat.email == user.email
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.name)
                                        Spacer(Modifier.width(8.dp))
                                        OnlineIndicator(isOnline = user.isOnline)
                                    }
                                },
                                supportingContent = {
                                    val statusText = if (user.isOnline) {
                                        "En línea"
                                    } else {
                                        user.lastSeen?.let { formatLastSeen(it) } ?: "Desconectado"
                                    }
                                    Text(statusText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                    .padding(horizontal = 8.dp)
                                    .clickable { onChatSelected(ChatDestination.Private(user.email)) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grupos", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreateGroup = true }) {
                Text("Nuevo grupo")
            }
        }
        Spacer(Modifier.height(4.dp))

        when {
            loadingGroups -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Cargando grupos…")
            }
            groupsError != null -> {
                Text(
                    "Error al cargar grupos: $groupsError",
                    color = MaterialTheme.colorScheme.error
                )
            }
            groups.isEmpty() -> {
                Text("Todavía no hay grupos")
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(groups) { group ->
                        val isSelected = selectedChat is ChatDestination.Group && selectedChat.id == group.id
                        ListItem(
                            headlineContent = { Text(group.name) },
                            supportingContent = {
                                Text("Integrantes: ${group.members.size}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 8.dp)
                                .clickable {
                                    onChatSelected(ChatDestination.Group(group.id, group.name, group.members))
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Divider()
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}

@Composable
private fun CreateGroupDialog(
    availableUsers: List<UserItem>,
    isProcessing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
        },
        title = { Text("Crear grupo") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Selecciona integrantes", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (availableUsers.isEmpty()) {
                    Text("No hay otros usuarios disponibles aún")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(availableUsers) { user ->
                            val checked = selectedMembers.contains(user.email)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        enabled = !isProcessing,
                                        onValueChange = { value ->
                                            selectedMembers = if (value) {
                                                selectedMembers + user.email
                                            } else {
                                                selectedMembers - user.email
                                            }
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    enabled = !isProcessing
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(user.name)
                                    Text(user.email, style = MaterialTheme.typography.bodySmall)
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
        }
    )
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

private fun formatLastSeen(lastSeen: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = lastSeen }
    val formatter: DateFormat = if (isSameDay(calendar.timeInMillis, System.currentTimeMillis())) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    } else {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    }
    return "Últ. vez ${formatter.format(Date(lastSeen))}"
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
