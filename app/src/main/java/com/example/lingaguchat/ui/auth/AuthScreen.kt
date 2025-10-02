package com.example.lingaguchat.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onClearError: () -> Unit
) {
    var isLoginMode by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLoginMode) "Iniciar sesión" else "Crear cuenta",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        AuthTextField(
            value = email,
            onValueChange = {
                email = it
                onClearError()
            },
            label = "Correo electrónico",
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(16.dp))

        AuthTextField(
            value = password,
            onValueChange = {
                password = it
                onClearError()
            },
            label = "Contraseña",
            imeAction = if (isLoginMode) ImeAction.Done else ImeAction.Next,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            onDone = {
                if (isLoginMode) {
                    onSignIn(email, password)
                }
            }
        )

        if (!isLoginMode) {
            Spacer(modifier = Modifier.height(16.dp))

            AuthTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    onClearError()
                },
                label = "Confirmar contraseña",
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password,
                isPassword = true,
                onDone = {
                    onSignUp(email, password, confirmPassword)
                }
            )
        }

        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isLoginMode) {
                    onSignIn(email, password)
                } else {
                    onSignUp(email, password, confirmPassword)
                }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text = if (isLoginMode) "Ingresar" else "Registrarme")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = {
            isLoginMode = !isLoginMode
            confirmPassword = ""
            onClearError()
        }) {
            Text(
                text = if (isLoginMode) {
                    "¿No tienes cuenta? Regístrate"
                } else {
                    "¿Ya tienes cuenta? Inicia sesión"
                }
            )
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    val keyboardActions = remember(onDone) {
        KeyboardActions(onDone = { onDone?.invoke() })
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
fun ChatHomeScreen(
    email: String,
    isLoading: Boolean,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sesión iniciada",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Bienvenido $email",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSignOut,
            enabled = !isLoading
        ) {
            Text(text = "Cerrar sesión")
        }
    }
}
