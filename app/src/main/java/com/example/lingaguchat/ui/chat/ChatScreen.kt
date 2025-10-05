package com.example.lingaguchat.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    destination: ChatDestination,
    chatViewModel: ChatViewModel = viewModel(),
    onOpenDrawer: () -> Unit
) {
    val messages by chatViewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSendingImage by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri }

    val db = remember { FirebaseFirestore.getInstance() }

    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var groupMembers by remember { mutableStateOf<List<String>>(if (destination is ChatDestination.Group) destination.members else emptyList()) }

    DisposableEffect(destination) {
        val usersCollection = db.collection("users")
        var meReg: ListenerRegistration? = null
        var contactReg: ListenerRegistration? = null
        val groupRegs = mutableListOf<ListenerRegistration>()
        var groupInfoReg: ListenerRegistration? = null

        memberNames = emptyMap()
        subtitle = null

        when (destination) {
            is ChatDestination.Private -> {
                title = destination.email.substringBefore("@")
                chatViewModel.listenPrivateMessages(currentUserEmail, destination.email)

                meReg = usersCollection.document(currentUserEmail)
                    .addSnapshotListener { snapshot, _ ->
                        val meName = snapshot?.getString("name") ?: currentUserEmail.substringBefore("@")
                        memberNames = memberNames.toMutableMap().apply { put(currentUserEmail, meName) }
                    }

                contactReg = usersCollection.document(destination.email)
                    .addSnapshotListener { snapshot, _ ->
                        val name = snapshot?.getString("name") ?: destination.email.substringBefore("@")
                        memberNames = memberNames.toMutableMap().apply { put(destination.email, name) }
                        val isOnline = snapshot?.getBoolean("online") ?: false
                        val lastSeen = snapshot?.getLong("lastSeen")
                        subtitle = if (isOnline) {
                            "En línea"
                        } else {
                            lastSeen?.let { formatLastSeen(it) } ?: "Desconectado"
                        }
                        title = name
                    }
            }

            is ChatDestination.Group -> {
                title = destination.name
                groupMembers = destination.members
                chatViewModel.listenGroupMessages(destination.id)

                groupInfoReg = db.collection("groups")
                    .document(destination.id)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && snapshot.exists()) {
                            title = snapshot.getString("name") ?: destination.name
                            val members = snapshot.get("members") as? List<String>
                            if (!members.isNullOrEmpty()) groupMembers = members
                        }
                    }

                val allMembers = (destination.members + currentUserEmail).distinct()
                allMembers.forEach { email ->
                    val reg = usersCollection.document(email)
                        .addSnapshotListener { snapshot, _ ->
                            val name = snapshot?.getString("name") ?: email.substringBefore("@")
                            memberNames = memberNames.toMutableMap().apply { put(email, name) }
                        }
                    groupRegs += reg
                }
            }
        }

        onDispose {
            meReg?.remove()
            contactReg?.remove()
            groupRegs.forEach { it.remove() }
            groupInfoReg?.remove()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    if (destination is ChatDestination.Group && groupMembers.isNotEmpty()) {
                        val membersText = groupMembers
                            .map { memberNames[it] ?: it.substringBefore("@") }
                            .joinToString(", ")
                        Text(
                            text = "Miembros: $membersText",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { onOpenDrawer() }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            state = listState
        ) {
            items(items = messages, key = { it.id }) { msg ->
                val isMe = msg.sender == currentUserEmail
                val who = memberNames[msg.sender] ?: msg.sender.substringBefore("@")

                // Usa tiempo del servidor (fallback al local) y muestra con segundos
                val effectiveTs = msg.serverTime?.toDate()?.time ?: msg.timestamp
                val timeText = SimpleDateFormat(
                    if (isSameDay(effectiveTs, System.currentTimeMillis()))
                        "HH:mm:ss" else "dd MMM yyyy HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(effectiveTs))

                MessageBubble(
                    name = who,
                    message = msg,
                    isCurrentUser = isMe,
                    timeText = timeText
                )
            }
        }

        AnimatedVisibility(visible = selectedImageUri != null) {
            selectedImageUri?.let { imageUri ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Imagen a enviar",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Quitar imagen"
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(imageVector = Icons.Default.Image, contentDescription = "Seleccionar imagen")
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        ) {
                            if (text.isBlank()) {
                                Text(
                                    text = "Escribe un mensaje...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            val sendEnabled = !isSendingImage && (text.isNotBlank() || selectedImageUri != null)
            Button(
                onClick = {
                    val trimmedText = text.trim()
                    val imageUri = selectedImageUri
                    when {
                        imageUri != null -> {
                            isSendingImage = true
                            when (destination) {
                                is ChatDestination.Private -> {
                                    chatViewModel.sendDirectImageMessage(
                                        sender = currentUserEmail,
                                        receiver = destination.email,
                                        imageUri = imageUri,
                                        caption = trimmedText
                                    ) { success ->
                                        isSendingImage = false
                                        if (success) {
                                            text = ""
                                            selectedImageUri = null
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "No se pudo enviar la imagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                is ChatDestination.Group -> {
                                    chatViewModel.sendGroupImageMessage(
                                        sender = currentUserEmail,
                                        groupId = destination.id,
                                        participants = groupMembers,
                                        imageUri = imageUri,
                                        caption = trimmedText
                                    ) { success ->
                                        isSendingImage = false
                                        if (success) {
                                            text = ""
                                            selectedImageUri = null
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "No se pudo enviar la imagen",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                        trimmedText.isNotBlank() -> {
                            when (destination) {
                                is ChatDestination.Private -> chatViewModel.sendDirectMessage(
                                    currentUserEmail,
                                    destination.email,
                                    trimmedText
                                )
                                is ChatDestination.Group -> chatViewModel.sendGroupMessage(
                                    sender = currentUserEmail,
                                    groupId = destination.id,
                                    participants = groupMembers,
                                    text = trimmedText
                                )
                            }
                            text = ""
                        }
                    }
                },
                enabled = sendEnabled
            ) {
                if (isSendingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Enviar")
                }
            }
        }
    }
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

@Composable
private fun MessageBubble(
    name: String,
    message: Message,
    isCurrentUser: Boolean,
    timeText: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            when (message.type) {
                MessageType.IMAGE -> {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        message.imageUrl?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Imagen enviada",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .sizeIn(maxWidth = 220.dp, maxHeight = 220.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                MessageType.TEXT -> {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
