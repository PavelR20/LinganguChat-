package com.example.lingaguchat.push

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Se encarga de almacenar los tokens FCM en el documento del usuario.
 */
object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    fun storeToken(email: String, token: String) {
        val cleanEmail = email.trim()
        val cleanToken = token.trim()
        if (cleanEmail.isEmpty() || cleanToken.isEmpty()) return

        val docRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(cleanEmail)

        docRef.update("fcmTokens", FieldValue.arrayUnion(cleanToken))
            .addOnSuccessListener {
                Log.d(TAG, "Token actualizado para $cleanEmail")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Fallo update() para $cleanEmail: ${error.message}")
                val payload = mapOf(
                    "fcmTokens" to FieldValue.arrayUnion(cleanToken),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Token guardado via set() para $cleanEmail")
                    }
                    .addOnFailureListener { inner ->
                        Log.e(TAG, "No se pudo guardar token para $cleanEmail", inner)
                    }
            }
    }
}
