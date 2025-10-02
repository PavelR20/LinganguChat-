package com.example.lingaguchat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lingaguchat.ui.auth.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    chatViewModel: ChatViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        // Barra superior con botón de logout
        TopAppBar(
            title = { Text("Chat") },
            actions = {
                TextButton(onClick = { authViewModel.signOut() }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.sender == currentUserEmail

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = msg.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                            .format(Date(msg.timestamp)),
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
                    val user = FirebaseAuth.getInstance().currentUser
                    chatViewModel.sendMessage(user?.email ?: "Desconocido", text)
                    text = ""
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
