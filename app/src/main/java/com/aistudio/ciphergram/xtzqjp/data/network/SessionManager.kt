package com.aistudio.ciphergram.xtzqjp.data.network

import android.content.Context
import android.util.Log
import com.aistudio.ciphergram.xtzqjp.data.model.UserProfile
import com.aistudio.ciphergram.xtzqjp.security.CryptoEngine
import com.facebook.AccessToken
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manages authentication state using Firebase Auth + Facebook Login.
 *
 * Responsibilities:
 *  - Sign in/out via Facebook OAuth → Firebase credential
 *  - On first login: generates ECDH key pair, stores public key in Firestore
 *  - Updates [UserProfile] in Firestore on each login (refreshes last seen, photo, etc.)
 *  - Provides access to the current [FirebaseUser] and [UserProfile]
 */
class SessionManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isAuthenticated: Boolean get() = currentUser != null

    companion object {
        private const val TAG = "SessionManager"
        const val USERS_COLLECTION = "users"
    }

    /**
     * Signs into Firebase using a Facebook [AccessToken].
     * Called from [SessionScreen] after successful Facebook Login.
     *
     * @return The [UserProfile] stored in Firestore on success, null on failure.
     */
    suspend fun signInWithFacebook(accessToken: AccessToken): UserProfile? {
        return try {
            val credential = FacebookAuthProvider.getCredential(accessToken.token)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: return null

            Log.d(TAG, "Firebase sign-in success: ${user.uid}")

            // Build or refresh the Firestore user profile
            val profile = buildUserProfile(user)
            saveOrUpdateUserProfile(profile)
            profile
        } catch (e: Exception) {
            Log.e(TAG, "Facebook Firebase sign-in failed", e)
            null
        }
    }

    /**
     * Builds a [UserProfile] for the given Firebase user,
     * generating an ECDH key pair if not already stored locally.
     */
    private fun buildUserProfile(user: FirebaseUser): UserProfile {
        val displayName = user.displayName ?: "CipherGram User"
        val publicKey = CryptoEngine.getPublicKeyBase64(context).also {
            // Ensure key pair exists (generates if missing)
            if (it.isEmpty()) CryptoEngine.getOrCreateKeyPair(context)
        }.ifEmpty { CryptoEngine.getPublicKeyBase64(context) }

        return UserProfile(
            uid = user.uid,
            displayName = displayName,
            searchName = displayName.lowercase(),
            photoUrl = user.photoUrl?.toString() ?: "",
            facebookId = user.providerData
                .firstOrNull { it.providerId == "facebook.com" }?.uid ?: "",
            publicKey = publicKey,
            lastSeen = System.currentTimeMillis()
        )
    }

    /**
     * Writes (or merges) the user profile to Firestore.
     * If the document already exists, only mutable fields are updated.
     */
    private suspend fun saveOrUpdateUserProfile(profile: UserProfile) {
        try {
            val existingDoc = db.collection(USERS_COLLECTION).document(profile.uid).get().await()
            if (existingDoc.exists()) {
                // Update only mutable fields to preserve existing public key
                db.collection(USERS_COLLECTION).document(profile.uid).update(
                    mapOf(
                        "displayName" to profile.displayName,
                        "searchName" to profile.searchName,
                        "photoUrl" to profile.photoUrl,
                        "lastSeen" to profile.lastSeen,
                        // Always update public key in case it was regenerated
                        "publicKey" to profile.publicKey
                    )
                ).await()
            } else {
                // First-time registration — create full document
                db.collection(USERS_COLLECTION).document(profile.uid).set(profile).await()
            }
            Log.d(TAG, "UserProfile saved/updated in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user profile", e)
        }
    }

    /**
     * Fetches the current user's [UserProfile] from Firestore.
     */
    suspend fun getCurrentUserProfile(): UserProfile? {
        val uid = currentUser?.uid ?: return null
        return try {
            val doc = db.collection(USERS_COLLECTION).document(uid).get().await()
            doc.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch current user profile", e)
            null
        }
    }

    /**
     * Signs out from Firebase Auth and clears the ECDH key cache.
     */
    fun signOut() {
        CryptoEngine.clearKeyCache()
        auth.signOut()
        Log.d(TAG, "User signed out")
    }
}
