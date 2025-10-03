package com.example.lingaguchat.ui.chat

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Simple AES helper to keep messages private at rest in Firestore.
 * It is not meant to be bulletproof security but prevents texto plano.
 */
object MessageCipher {
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private val charset = Charsets.UTF_8
    private val keySpec = SecretKeySpec("LinganguChatKey!".toByteArray(charset), "AES")
    private val ivSpec = IvParameterSpec("LinganguChatIV!!".toByteArray(charset))

    fun encrypt(plain: String): String {
        if (plain.isBlank()) return plain
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plain.toByteArray(charset))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrElse { plain }
    }

    fun decrypt(encrypted: String): String {
        if (encrypted.isBlank()) return encrypted
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(encrypted, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), charset)
        }.getOrElse { encrypted }
    }
}
