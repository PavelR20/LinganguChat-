package com.example.lingaguchat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lingaguchat.ui.auth.AuthScreen
import com.example.lingaguchat.ui.auth.AuthViewModel
import com.example.lingaguchat.ui.chat.ChatDestination
import com.example.lingaguchat.ui.chat.ChatScreen
import com.example.lingaguchat.ui.chat.DrawerContent
import com.example.lingaguchat.ui.chat.PresenceManager
import com.example.lingaguchat.ui.theme.LingaguChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Permiso de notificaciones (Android 13+)
            val needsPermission = Build.VERSION.SDK_INT >= 33
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { /* opcional: manejar resultado */ }
            )
            LaunchedEffect(needsPermission) {
                if (needsPermission) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LingaguChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val uiState by authViewModel.uiState

                    if (uiState.isAuthenticated) {
                        val currentEmail = uiState.userEmail.orEmpty()

                        // Presencia online/offline
                        LaunchedEffect(currentEmail) {
                            if (currentEmail.isNotBlank()) {
                                PresenceManager.setOnline(currentEmail)
                            }
                        }
                        DisposableEffect(currentEmail) {
                            onDispose {
                                if (currentEmail.isNotBlank()) {
                                    PresenceManager.setOffline(currentEmail)
                                }
                            }
                        }

                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()

                        // 👉 Declarado ANTES de usarlo en el LaunchedEffect del deep-link
                        var selectedChat by remember { mutableStateOf<ChatDestination?>(null) }

                        // Deep-link desde notificación
                        LaunchedEffect(Unit) {
                            val extras = this@MainActivity.intent?.extras
                            val open = extras?.getBoolean("openFromNotification") == true
                            if (open) {
                                val destType = extras?.getString("destType")
                                val peer = extras?.getString("peer")
                                if (!peer.isNullOrBlank()) {
                                    selectedChat = when (destType) {
                                        "group" -> ChatDestination.Group(
                                            id = peer,
                                            name = "Grupo",
                                            members = emptyList()
                                        )
                                        else -> ChatDestination.Private(email = peer)
                                    }
                                }
                                // Limpia el extra para evitar reabrir al rotar
                                this@MainActivity.intent?.removeExtra("openFromNotification")
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                                    drawerContentColor = MaterialTheme.colorScheme.onSurface
                                ) {
                                    DrawerContent(
                                        currentUserEmail = currentEmail,
                                        selectedChat = selectedChat,
                                        onChatSelected = { chat ->
                                            selectedChat = chat
                                            scope.launch { drawerState.close() }
                                        },
                                        onSignOut = {
                                            PresenceManager.setOffline(currentEmail)
                                            authViewModel.signOut()
                                        }
                                    )
                                }
                            }
                        ) {
                            if (selectedChat != null) {
                                ChatScreen(
                                    currentUserEmail = currentEmail,
                                    destination = selectedChat!!,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Text(
                                        text = "Selecciona un chat en el menú",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        AuthScreen(
                            uiState = uiState,
                            onSignIn = authViewModel::signIn,
                            onSignUp = authViewModel::register,
                            onClearError = authViewModel::clearError
                        )
                    }
                }
            }
        }
    }
}
