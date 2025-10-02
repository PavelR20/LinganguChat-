package com.example.lingaguchat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
            modifier = Modifier.weight(1f).padding(8.dp)
        ) {
            items(items = messages, key = { it.id }) { msg ->
                val isMe = msg.sender == currentUserEmail
                val who = if (isMe) meName else contactName
                val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(msg.timestamp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).padding(8.dp)
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
