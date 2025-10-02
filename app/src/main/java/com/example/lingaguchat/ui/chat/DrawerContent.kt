package com.example.lingaguchat.ui.chat

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class UserItem(
    val email: String,
    val name: String
)

@Composable
fun DrawerContent(
    currentUserEmail: String,
    selectedUser: String?,
    onAccountSelected: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var accounts by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Registrar el listener una sola vez y limpiarlo al salir
    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("users")
            .addSnapshotListener { snapshot, e ->
                loading = false
                if (e != null) {
                    errorMsg = e.message
                    Log.e("DrawerContent", "Firestore error", e)
                    accounts = emptyList()
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val email = doc.getString("email")
                    val name = doc.getString("name")
                    if (email.isNullOrBlank() || name.isNullOrBlank()) null
                    else UserItem(email, name)
                }.orEmpty()
                accounts = list
            }
        onDispose { reg?.remove() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Selecciona un contacto", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> {
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
                    otherUsers.forEach { user ->
                        TextButton(onClick = { onAccountSelected(user.email) }) {
                            Text(
                                text = if (user.email == selectedUser)
                                    "${user.name} (en chat)" else user.name
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSignOut) { Text("Cerrar sesión") }
    }
}
