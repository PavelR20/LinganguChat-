package com.example.lingaguchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            LingaguChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val uiState by authViewModel.uiState

                    if (uiState.isAuthenticated) {
                        val currentEmail = uiState.userEmail.orEmpty()
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

                        var selectedChat by remember { mutableStateOf<ChatDestination?>(null) }

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
