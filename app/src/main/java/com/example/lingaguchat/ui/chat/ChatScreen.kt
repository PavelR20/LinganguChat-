package com.example.lingaguchat.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    selectedUserEmail: String,
    chatViewModel: ChatViewModel = viewModel(),
    onOpenDrawer: () -> Unit
) {
    val messages by chatViewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            chatViewModel.sendImageMessage(currentUserEmail, selectedUserEmail, it) { success ->
                if (!success) {
                    Toast.makeText(context, "No se pudo enviar la imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // nombres para UI
    var contactName by remember { mutableStateOf(selectedUserEmail.substringBefore("@")) }
    var meName by remember { mutableStateOf(currentUserEmail.substringBefore("@")) }

    // Cargar nombres y activar listeners del chat
    LaunchedEffect(selectedUserEmail) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(selectedUserEmail).get()
            .addOnSuccessListener { doc ->
                contactName = doc.getString("name") ?: selectedUserEmail.substringBefore("@")
            }
        db.collection("users").document(currentUserEmail).get()
            .addOnSuccessListener { doc ->
                meName = doc.getString("name") ?: currentUserEmail.substringBefore("@")
            }
        chatViewModel.listenPrivateMessages(currentUserEmail, selectedUserEmail)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Chat con $contactName") },
            navigationIcon = {
                IconButton(onClick = { onOpenDrawer() }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(items = messages, key = { it.id }) { msg ->
                val isMe = msg.sender == currentUserEmail
                val who = if (isMe) meName else contactName
                val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(msg.timestamp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = who,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isMe)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        when (msg.type) {
                            MessageType.IMAGE -> {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    msg.imageUrl?.let { imageUrl ->
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = "Imagen enviada",
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .sizeIn(maxWidth = 220.dp, maxHeight = 220.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (msg.text.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            MessageType.TEXT -> {
                                Text(
                                    text = msg.text,
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
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = "Enviar imagen")
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            )
            Button(onClick = {
                if (text.isNotBlank()) {
                    chatViewModel.sendMessage(currentUserEmail, selectedUserEmail, text)
                    text = ""
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
