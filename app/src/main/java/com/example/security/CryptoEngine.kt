package com.example.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECDH-based End-to-End Encryption Engine.
 *
 * Key exchange model:
 *   1. Each user generates a P-256 (secp256r1) EC key pair on first launch.
 *   2. The private key is encrypted via [LocalCryptoEngine] (AndroidKeyStore AES-GCM)
 *      and stored in SharedPreferences.
 *   3. The public key (Base64-encoded) is stored in Firestore under /users/{uid}/publicKey.
 *   4. When sending a message to user B:
 *        sharedSecret = ECDH(myPrivateKey, B.publicKey)
 *        aesKey       = SHA-256(sharedSecret)
 *        ciphertext   = AES-256-GCM(plaintext, aesKey)
 *   5. When user B receives the message:
 *        sharedSecret = ECDH(B.privateKey, myPublicKey)  ← same value (DH property)
 *        aesKey       = SHA-256(sharedSecret)
 *        plaintext    = AES-256-GCM-Decrypt(ciphertext, aesKey)
 *
 * This means no manual passphrase is needed — E2EE is automatic.
 */
object CryptoEngine {
    private const val TAG = "CryptoEngine"
    private const val PREFS_NAME = "ciphergram_crypto_prefs"
    private const val KEY_PRIVATE = "ec_private_key_encrypted"
    private const val KEY_PUBLIC = "ec_public_key"
    private const val ALGORITHM = "EC"
    private const val CURVE = "secp256r1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    const val ENCRYPTED_PREFIX = "ENC:"

    // In-memory cache of derived shared keys per remote UID
    private val sharedKeyCache = mutableMapOf<String, SecretKeySpec>()

    // ── Key Pair Management ─────────────────────────────────────────────────

    /**
     * Returns the EC key pair, generating and persisting it if not already stored.
     * Private key is encrypted via [LocalCryptoEngine] before storage.
     */
    fun getOrCreateKeyPair(context: Context): KeyPair {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPrivate = prefs.getString(KEY_PRIVATE, null)
        val storedPublic = prefs.getString(KEY_PUBLIC, null)

        if (encryptedPrivate != null && storedPublic != null) {
            return try {
                // Restore private key
                val privateB64 = LocalCryptoEngine.decrypt(encryptedPrivate)
                val privateBytes = Base64.decode(privateB64, Base64.NO_WRAP)
                val privateKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(PKCS8EncodedKeySpec(privateBytes))

                // Restore public key
                val publicBytes = Base64.decode(storedPublic, Base64.NO_WRAP)
                val publicKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(X509EncodedKeySpec(publicBytes))

                KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore key pair, regenerating", e)
                generateAndStoreKeyPair(context, prefs)
            }
        }

        return generateAndStoreKeyPair(context, prefs)
    }

    private fun generateAndStoreKeyPair(
        context: Context,
        prefs: android.content.SharedPreferences
    ): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM)
        keyPairGen.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        // Encrypt private key with hardware-backed AndroidKeyStore key
        val privateB64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val encryptedPrivate = LocalCryptoEngine.encrypt(privateB64)

        val publicB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PRIVATE, encryptedPrivate)
            .putString(KEY_PUBLIC, publicB64)
            .apply()

        Log.d(TAG, "Generated new ECDH key pair")
        return keyPair
    }

    /**
     * Returns the Base64-encoded EC public key for Firestore storage.
     */
    fun getPublicKeyBase64(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PUBLIC, null)
            ?: Base64.encodeToString(getOrCreateKeyPair(context).public.encoded, Base64.NO_WRAP)
    }

    // ── ECDH Key Agreement ──────────────────────────────────────────────────

    /**
     * Derives a shared AES-256 key for communicating with a remote user.
     * Results are cached in memory for the lifetime of the process.
     *
     * @param remoteUid  UID of the remote user (used as cache key)
     * @param remotePublicKeyBase64  Base64 EC public key from Firestore
     * @param myPrivateKey  The local user's EC private key
     */
    fun getSharedKey(
        remoteUid: String,
        remotePublicKeyBase64: String,
        myPrivateKey: PrivateKey
    ): SecretKeySpec {
        sharedKeyCache[remoteUid]?.let { return it }

        val remotePublicBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val remotePublicKey = KeyFactory.getInstance(ALGORITHM)
            .generatePublic(X509EncodedKeySpec(remotePublicBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(remotePublicKey, true)
        val sharedSecretBytes = keyAgreement.generateSecret()

        // Derive 256-bit AES key via SHA-256 of the raw shared secret
        val aesKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecretBytes)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        sharedKeyCache[remoteUid] = aesKey
        Log.d(TAG, "Derived ECDH shared key for $remoteUid")
        return aesKey
    }

    /** Clears the in-memory key cache (e.g., on logout). */
    fun clearKeyCache() {
        sharedKeyCache.clear()
    }

    // ── AES-GCM Encryption / Decryption ────────────────────────────────────

    /**
     * Encrypts [plainText] with the given AES key.
     * Returns a self-describing packet: "ENC:[base64(IV || Ciphertext)]"
     */
    fun encrypt(plainText: String, key: SecretKeySpec): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + ciphertextBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertextBytes, 0, combined, iv.size, ciphertextBytes.size)

            "$ENCRYPTED_PREFIX${Base64.encodeToString(combined, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            plainText // Graceful fallback
        }
    }

    /**
     * Decrypts an "ENC:[base64]" packet. Returns original plaintext or an error string.
     */
    fun decrypt(encryptedText: String, key: SecretKeySpec): String {
        if (!isEncrypted(encryptedText)) return encryptedText
        return try {
            val combined = Base64.decode(
                encryptedText.removePrefix(ENCRYPTED_PREFIX).trim(),
                Base64.NO_WRAP
            )
            if (combined.size < IV_LENGTH_BYTES) return "[Error: Packet too short]"

            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, iv.size)

            val ciphertextBytes = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, ciphertextBytes, 0, ciphertextBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertextBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            "[Decryption Failed — Wrong Key?]"
        }
    }

    fun isEncrypted(text: String): Boolean = text.startsWith(ENCRYPTED_PREFIX)

    // ── Legacy passphrase-based encrypt (kept for sandbox mode compatibility) ──

    /**
     * Derives a deterministic AES key from a plain string passphrase (SHA-256).
     * Used only in Sandbox mode where no ECDH key pair is available.
     */
    fun deriveKeyFromPassphrase(passphrase: String): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
