package com.example.lingaguchat.ui.chat

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object PresenceManager {
    private val usersCollection = FirebaseFirestore.getInstance().collection("users")

    fun setOnline(email: String) {
        val payload = mapOf(
            "online" to true,
            "lastSeen" to System.currentTimeMillis()
        )
        usersCollection.document(email).set(payload, SetOptions.merge())
    }

    fun setOffline(email: String) {
        val payload = mapOf(
            "online" to false,
            "lastSeen" to System.currentTimeMillis()
        )
        usersCollection.document(email).set(payload, SetOptions.merge())
    }
}
