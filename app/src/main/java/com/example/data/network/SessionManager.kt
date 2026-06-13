package com.example.data.network

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("ig_secure_session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "ig_session_key_alias"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        
        private const val PREF_SESSION_COOKIES = "session_cookies_encrypted"
        private const val PREF_IS_AUTHENTICATED = "is_authenticated"
        private const val PREF_USERNAME = "ig_username"
        private const val PREF_CUSTOM_USER_AGENT = "custom_user_agent_encrypted"
        private const val PREF_CUSTOM_SESSION_TOKEN = "custom_session_token_encrypted"
        private const val PREF_TARGET_USER_ID = "target_user_id_encrypted"
    }

    init {
        initKeystoreKey()
    }

    private fun initKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                 .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                 .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    private fun encrypt(plainText: String): String? {
        if (plainText.isEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val ciphertextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertextBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertextBytes, 0, combined, iv.size, ciphertextBytes.size)
            Base64.encodeToString(combined, Base64.DEFAULT or Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decrypt(encryptedString: String): String? {
        if (encryptedString.isEmpty()) return null
        return try {
            val combined = Base64.decode(encryptedString, Base64.DEFAULT)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            val ciphertextBytes = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, ciphertextBytes, 0, ciphertextBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val plainBytes = cipher.doFinal(ciphertextBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveCookies(cookiesString: String, username: String) {
        val encrypted = encrypt(cookiesString) ?: return
        prefs.edit()
            .putString(PREF_SESSION_COOKIES, encrypted)
            .putString(PREF_USERNAME, username)
            .putBoolean(PREF_IS_AUTHENTICATED, true)
            .apply()
    }

    fun saveManualSession(
        username: String,
        targetUserId: String,
        sessionToken: String,
        csrfToken: String,
        userAgent: String
    ) {
        val encTarget = encrypt(targetUserId) ?: ""
        val encSessionToken = encrypt(sessionToken) ?: ""
        val encCsrfToken = encrypt(csrfToken) ?: ""
        val encUA = encrypt(userAgent) ?: ""

        // Wrap as cookies for backward compatibility
        val formattedCookies = "sessionid=$sessionToken; ds_user_id=$targetUserId; csrftoken=$csrfToken"
        val encCookies = encrypt(formattedCookies) ?: ""

        prefs.edit()
            .putString(PREF_USERNAME, username)
            .putString(PREF_TARGET_USER_ID, encTarget)
            .putString(PREF_CUSTOM_SESSION_TOKEN, encSessionToken)
            .putString(PREF_SESSION_COOKIES, encCookies)
            .putString(PREF_CUSTOM_USER_AGENT, encUA)
            .putBoolean(PREF_IS_AUTHENTICATED, true)
            .apply()
    }

    fun getTargetUserId(): String {
        val encrypted = prefs.getString(PREF_TARGET_USER_ID, "") ?: ""
        if (encrypted.isEmpty()) return ""
        return decrypt(encrypted) ?: ""
    }

    fun getSessionToken(): String {
        val encrypted = prefs.getString(PREF_CUSTOM_SESSION_TOKEN, "") ?: ""
        if (encrypted.isEmpty()) return ""
        return decrypt(encrypted) ?: ""
    }

    fun getUserAgent(): String {
        val encrypted = prefs.getString(PREF_CUSTOM_USER_AGENT, "") ?: ""
        if (encrypted.isEmpty()) return ""
        return decrypt(encrypted) ?: ""
    }

    fun getCookies(): String {
        val encrypted = prefs.getString(PREF_SESSION_COOKIES, "") ?: ""
        if (encrypted.isEmpty()) return ""
        return decrypt(encrypted) ?: ""
    }

    fun getUsername(): String {
        return prefs.getString(PREF_USERNAME, "Guest User") ?: "Guest User"
    }

    fun isSandboxMode(): Boolean {
        return prefs.getBoolean("is_sandbox_mode", false)
    }

    fun setSandboxMode(active: Boolean) {
        prefs.edit()
            .putBoolean("is_sandbox_mode", active)
            .putBoolean(PREF_IS_AUTHENTICATED, active)
            .putString(PREF_USERNAME, if (active) "sandbox_developer" else "Guest User")
            .apply()
    }

    fun isAuthenticated(): Boolean {
        return isSandboxMode() || (prefs.getBoolean(PREF_IS_AUTHENTICATED, false) && getCookies().isNotEmpty())
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
