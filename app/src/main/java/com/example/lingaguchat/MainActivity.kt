package com.example.lingaguchat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lingaguchat.ui.auth.AuthScreen
import com.example.lingaguchat.ui.auth.AuthViewModel
import com.example.lingaguchat.ui.chat.ChatDestination
import com.example.lingaguchat.ui.chat.ChatScreen
import com.example.lingaguchat.ui.chat.HomeScreen
import com.example.lingaguchat.ui.chat.PresenceManager
import com.example.lingaguchat.ui.theme.LingaguChatTheme

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

                        // 👉 Declarado ANTES de usarlo en el LaunchedEffect del deep-link
                        var selectedChat by remember { mutableStateOf<ChatDestination?>(null) }
                        val homeSnackbarHostState = remember { SnackbarHostState() }

                        // Deep-link desde notificación
                        LaunchedEffect(Unit) {
                            val extras = this@MainActivity.intent?.extras
                            val open = extras?.getBoolean("openFromNotification") == true
                            if (open) {
                                val destType = extras?.getString("destType")
                                val peer = extras?.getString("peer")
                                val chatId = extras?.getString("chatId")
                                if (!peer.isNullOrBlank()) {
                                    selectedChat = when (destType) {
                                        "group" -> ChatDestination.Group(
                                            chatId = chatId ?: peer,
                                            name = extras?.getString("groupName") ?: "Grupo",
                                            members = emptyList()
                                        )
                                        else -> ChatDestination.Direct(
                                            chatId = chatId ?: peer,
                                            peerEmail = peer,
                                            members = listOf(currentEmail, peer)
                                        )
                                    }
                                }
                                // Limpia el extra para evitar reabrir al rotar
                                this@MainActivity.intent?.removeExtra("openFromNotification")
                            }
                        }

                        if (selectedChat == null) {
                            HomeScreen(
                                currentUserEmail = currentEmail,
                                onOpenChat = { destination -> selectedChat = destination },
                                onSignOut = {
                                    PresenceManager.setOffline(currentEmail)
                                    authViewModel.signOut()
                                },
                                snackbarHostState = homeSnackbarHostState
                            )
                        } else {
                            BackHandler { selectedChat = null }
                            ChatScreen(
                                currentUserEmail = currentEmail,
                                destination = selectedChat!!,
                                onBack = { selectedChat = null }
                            )
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
