package com.aistudio.ciphergram.xtzqjp.data.model

/**
 * Represents a registered CipherGram user, stored in Firestore.
 * Collection path: /users/{uid}
 *
 * [publicKey] holds the Base64-encoded EC public key (secp256r1 / P-256 curve).
 * This key is used by other users to perform ECDH key agreement to derive
 * a shared AES-256 key for E2EE message encryption.
 *
 * [searchName] is the lowercase [displayName] used for prefix-based user search queries.
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val searchName: String = "",     // lowercase displayName for Firestore prefix search
    val photoUrl: String = "",
    val facebookId: String = "",
    val publicKey: String = "",      // Base64 EC public key (P-256)
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)
