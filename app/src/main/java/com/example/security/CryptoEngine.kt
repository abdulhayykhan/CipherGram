package com.example.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val PREFIX = "ENC:"

    /**
     * Derives a 32-byte (256-bit) AES key from a given string password/secret using SHA-256.
     */
    fun deriveKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hashedBytes, ALGORITHM)
    }

    /**
     * Encrypts plain text using the derived key.
     * Prepends the random IV (12 bytes) to the ciphertext, encodes to Base64, and wraps in packet space.
     */
    fun encrypt(plainText: String, secret: String): String {
        try {
            val key = deriveKey(secret)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key) // Automatically generates random IV
            val iv = cipher.iv ?: ByteArray(IV_LENGTH_BYTES).apply { java.security.SecureRandom().nextBytes(this) }
            val ciphertextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // Combine IV and ciphertext: [IV (12 bytes)][Ciphertext (variable)]
            val combined = ByteArray(iv.size + ciphertextBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertextBytes, 0, combined, iv.size, ciphertextBytes.size)
            
            val base64 = Base64.encodeToString(combined, Base64.DEFAULT or Base64.NO_WRAP)
            return "$PREFIX$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            return plainText
        }
    }

    /**
     * Decrypts the formatted ciphertext packet. Returns the original plain text.
     * Input envelope format: "ENC:[base64_string]"
     */
    fun decrypt(encryptedPayload: String, secret: String): String {
        if (!isEncrypted(encryptedPayload)) {
            return encryptedPayload
        }
        try {
            val key = deriveKey(secret)
            val base64Data = encryptedPayload.removePrefix(PREFIX).trim()
            val combined = Base64.decode(base64Data, Base64.DEFAULT)
            if (combined.size < IV_LENGTH_BYTES) {
                return "[Error: Ciphertext too short]"
            }
            
            // Split IV and ciphertext
            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            
            val ciphertextBytes = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, ciphertextBytes, 0, ciphertextBytes.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val plainBytes = cipher.doFinal(ciphertextBytes)
            return String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return "[Decryption Failed: Invalid/Mismatching Key]"
        }
    }

    fun isEncrypted(text: String): Boolean {
        return text.startsWith(PREFIX)
    }
}
