package com.example.lingaguchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lingaguchat.ui.auth.AuthScreen
import com.example.lingaguchat.ui.auth.AuthViewModel
import com.example.lingaguchat.ui.chat.ChatScreen   // 👈 importa tu ChatScreen
import com.example.lingaguchat.ui.theme.LingaguChatTheme

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
                        ChatScreen(
                            currentUserEmail = uiState.userEmail.orEmpty()
                        )
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
