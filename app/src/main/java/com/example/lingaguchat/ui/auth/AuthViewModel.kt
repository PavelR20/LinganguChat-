package com.example.lingaguchat.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userEmail: String? = FirebaseAuth.getInstance().currentUser?.email
) {
    val isAuthenticated: Boolean get() = userEmail != null
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _uiState = mutableStateOf(AuthUiState())
    val uiState: State<AuthUiState> = _uiState

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val currentUser = firebaseAuth.currentUser
        updateState {
            it.copy(
                isLoading = false,
                errorMessage = null,
                userEmail = currentUser?.email
            )
        }
        // Si hay usuario logueado, garantizamos su documento en Firestore
        currentUser?.email?.let { email ->
            ensureUserDoc(email)
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    /** Crea el doc en /users/{email} si no existe. Usa 'name' si se provee; si no, toma prefijo del email. */
    private fun ensureUserDoc(email: String, name: String? = null) {
        val db = FirebaseFirestore.getInstance()
        val users = db.collection("users")
        val docRef = users.document(email.trim())

        docRef.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    println("✅ users/$email ya existe")
                } else {
                    val finalName = (name ?: email.substringBefore("@")).trim()
                    val payload = mapOf("email" to email.trim(), "name" to finalName)
                    docRef.set(payload)
                        .addOnSuccessListener {
                            println("✅ Creado users/$email con name='$finalName'")
                        }
                        .addOnFailureListener { e ->
                            println("❌ Error creando users/$email: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                // Si falló el get (reglas/permiso), intentamos set directo para forzar creación
                val finalName = (name ?: email.substringBefore("@")).trim()
                docRef.set(mapOf("email" to email.trim(), "name" to finalName))
                    .addOnSuccessListener {
                        println("✅ Creado (fallback) users/$email con name='$finalName'")
                    }
                    .addOnFailureListener { e2 ->
                        println("❌ Error (fallback) users/$email: ${e.message} / ${e2.message}")
                    }
            }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            updateState { it.copy(errorMessage = "Ingrese correo y contraseña.") }
            return
        }

        updateState { it.copy(isLoading = true, errorMessage = null) }

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val current = auth.currentUser?.email ?: email.trim()
                    // Garantizar doc para cuentas “viejas”
                    ensureUserDoc(current)
                } else {
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

    fun register(email: String, password: String, confirmPassword: String, username: String) {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank() || username.isBlank()) {
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
                if (task.isSuccessful) {
                    val current = auth.currentUser?.email ?: email.trim()
                    // Crear doc con el nombre elegido
                    ensureUserDoc(current, username)
                } else {
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
