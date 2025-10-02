package com.example.lingaguchat.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth


data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userEmail: String? = Firebase.auth.currentUser?.email
) {
    val isAuthenticated: Boolean get() = userEmail != null
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val currentUser = firebaseAuth.currentUser
        updateState {
            it.copy(
                isLoading = false,
                errorMessage = null,
                userEmail = currentUser?.email
            )
        }
    }

    private val _uiState = mutableStateOf(AuthUiState())
    val uiState: State<AuthUiState> = _uiState

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            updateState { it.copy(errorMessage = "Ingrese correo y contraseña.") }
            return
        }

        updateState { it.copy(isLoading = true, errorMessage = null) }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = task.exception?.localizedMessage
                                ?: "No se pudo iniciar sesión."
                        )
                    }
                }
            }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            updateState { it.copy(errorMessage = "Complete todos los campos.") }
            return
        }

        if (password != confirmPassword) {
            updateState { it.copy(errorMessage = "Las contraseñas no coinciden.") }
            return
        }

        if (password.length < 6) {
            updateState { it.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres.") }
            return
        }

        updateState { it.copy(isLoading = true, errorMessage = null) }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = task.exception?.localizedMessage
                                ?: "No se pudo registrar."
                        )
                    }
                }
            }
    }

    fun signOut() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        auth.signOut()
    }

    fun clearError() {
        updateState { state ->
            if (state.errorMessage == null) state else state.copy(errorMessage = null)
        }
    }

    private fun updateState(transform: (AuthUiState) -> AuthUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
