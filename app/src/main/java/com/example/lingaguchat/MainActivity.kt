package com.example.lingaguchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lingaguchat.ui.auth.AuthScreen
import com.example.lingaguchat.ui.auth.AuthViewModel
import com.example.lingaguchat.ui.chat.ChatScreen
import com.example.lingaguchat.ui.chat.DrawerContent
import com.example.lingaguchat.ui.theme.LingaguChatTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

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
                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()

                        // 👇 estado para guardar el usuario seleccionado del drawer
                        var selectedUser by remember { mutableStateOf<String?>(null) }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                                    drawerContentColor = MaterialTheme.colorScheme.onSurface
                                ) {
                                    DrawerContent(
                                        currentUserEmail = uiState.userEmail.orEmpty(),
                                        selectedUser = selectedUser,
                                        onAccountSelected = { email ->
                                            selectedUser = email
                                            scope.launch { drawerState.close() }
                                        },
                                        onSignOut = { authViewModel.signOut() }
                                    )
                                }
                            }
                        ) {
                            if (selectedUser != null) {
                                ChatScreen(
                                    currentUserEmail = uiState.userEmail.orEmpty(),
                                    selectedUserEmail = selectedUser!!,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            } else {
                                // Pantalla vacía si no has seleccionado contacto
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Text(
                                        text = "Selecciona un contacto en el menú",
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
