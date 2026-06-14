package com.aistudio.ciphergram.xtzqjp.data.repository

import android.util.Log
import com.aistudio.ciphergram.xtzqjp.data.model.ChatMessage
import com.aistudio.ciphergram.xtzqjp.data.model.ChatThread
import com.aistudio.ciphergram.xtzqjp.data.model.UserProfile
import com.aistudio.ciphergram.xtzqjp.data.network.InstagramScraper
import com.aistudio.ciphergram.xtzqjp.data.network.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed data repository for CipherGram.
 *
 * Firestore schema:
 *   /users/{uid}                    → [UserProfile]
 *   /threads/{threadId}             → [ChatThread]
 *   /threads/{threadId}/messages/   → [ChatMessage] (auto-ID)
 *
 * Thread IDs are deterministic: sorted(uid1, uid2).joinToString("_")
 * This ensures both participants reference the same thread document.
 *
 * All message text stored in Firestore is always E2EE ciphertext ("ENC:...").
 * Decryption happens in [ChatViewModel] via [CryptoEngine].
 */
class ChatRepository(
    private val sessionManager: SessionManager
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val myUid get() = auth.currentUser?.uid ?: ""

    companion object {
        private const val TAG = "ChatRepository"
        const val USERS_COL = "users"
        const val THREADS_COL = "threads"
        const val MESSAGES_COL = "messages"

        /** Deterministic thread ID: sorted UIDs joined by underscore. */
        fun buildThreadId(uid1: String, uid2: String): String {
            return listOf(uid1, uid2).sorted().joinToString("_")
        }
    }

    // ── Threads ──────────────────────────────────────────────────────────────

    /**
     * Real-time Flow of all threads the current user participates in,
     * ordered by most recent message.
     */
    fun getThreadsFlow(): Flow<List<ChatThread>> = callbackFlow {
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(THREADS_COL)
            .whereArrayContains("participants", myUid)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Thread listener error", error)
                    return@addSnapshotListener
                }
                val threads = snapshot?.documents?.mapNotNull {
                    it.toObject(ChatThread::class.java)
                } ?: emptyList()
                trySend(threads)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time Flow of messages for a specific thread, oldest-first.
     */
    fun getMessagesFlow(threadId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = db.collection(THREADS_COL)
            .document(threadId)
            .collection(MESSAGES_COL)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Messages listener error", error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(
                        id = doc.id,
                        isSender = doc.getString("senderId") == myUid
                    )
                } ?: emptyList()
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Fetches a thread by ID (single read, not real-time).
     */
    suspend fun getThread(threadId: String): ChatThread? {
        return try {
            db.collection(THREADS_COL).document(threadId).get().await()
                .toObject(ChatThread::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get thread $threadId", e)
            null
        }
    }

    /**
     * Creates a new thread between the current user and [otherUser].
     * If the thread already exists, returns the existing thread ID.
     */
    suspend fun getOrCreateThread(otherUser: UserProfile): String {
        val threadId = buildThreadId(myUid, otherUser.uid)
        val myProfile = sessionManager.getCurrentUserProfile() ?: return threadId

        val existing = db.collection(THREADS_COL).document(threadId).get().await()
        if (!existing.exists()) {
            val thread = ChatThread(
                id = threadId,
                participants = listOf(myUid, otherUser.uid),
                participantNames = mapOf(
                    myUid to myProfile.displayName,
                    otherUser.uid to otherUser.displayName
                ),
                participantPics = mapOf(
                    myUid to myProfile.photoUrl,
                    otherUser.uid to otherUser.photoUrl
                ),
                lastMessage = "E2EE channel established 🔒",
                lastMessageTime = System.currentTimeMillis()
            )
            db.collection(THREADS_COL).document(threadId).set(thread).await()
            Log.d(TAG, "Created new thread: $threadId")
        }
        return threadId
    }

    suspend fun deleteThread(threadId: String) {
        try {
            // Delete all messages in the thread
            val messages = db.collection(THREADS_COL)
                .document(threadId)
                .collection(MESSAGES_COL)
                .get().await()
            messages.documents.forEach { it.reference.delete().await() }
            // Delete the thread document
            db.collection(THREADS_COL).document(threadId).delete().await()
            Log.d(TAG, "Deleted thread: $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete thread: $threadId", e)
        }
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    /**
     * Sends a message to Firestore. The [encryptedText] should already be
     * AES-GCM ciphertext (prefix "ENC:") produced by [CryptoEngine].
     *
     * @return The Firestore document ID of the new message.
     */
    suspend fun sendMessage(
        threadId: String,
        encryptedText: String,
        senderDisplayName: String
    ): String {
        val message = hashMapOf(
            "threadId" to threadId,
            "senderId" to myUid,
            "senderUsername" to senderDisplayName,
            "encryptedText" to encryptedText,
            "timestamp" to System.currentTimeMillis(),
            "isSender" to true,
            "isScrapedMedia" to false,
            "mediaImageUrl" to null,
            "mediaVideoUrl" to null,
            "mediaCaption" to null
        )

        val ref = db.collection(THREADS_COL)
            .document(threadId)
            .collection(MESSAGES_COL)
            .add(message).await()

        // Update thread preview
        val preview = if (encryptedText.startsWith("ENC:")) "[Encrypted Message 🔒]" else encryptedText
        db.collection(THREADS_COL).document(threadId).update(
            mapOf(
                "lastMessage" to preview,
                "lastMessageTime" to System.currentTimeMillis()
            )
        ).await()

        // Check if it's an Instagram URL — trigger scrape
        if (InstagramScraper.isInstagramUrl(encryptedText)) {
            triggerMediaScrape(threadId, ref.id, encryptedText)
        }

        return ref.id
    }

    /**
     * Scrapes Instagram media for a given URL and patches the Firestore message.
     */
    suspend fun triggerMediaScrape(threadId: String, messageId: String, url: String) {
        try {
            val result = InstagramScraper.scrapeMedia(url)
            db.collection(THREADS_COL)
                .document(threadId)
                .collection(MESSAGES_COL)
                .document(messageId)
                .update(
                    mapOf(
                        "isScrapedMedia" to true,
                        "mediaImageUrl" to result.imageUrl,
                        "mediaVideoUrl" to result.videoUrl,
                        "mediaCaption" to result.caption
                    )
                ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Media scrape failed for $messageId", e)
        }
    }

    // ── User Discovery ────────────────────────────────────────────────────────

    /**
     * Searches for users by display name prefix (case-insensitive).
     * Uses Firestore range query on the [searchName] field.
     * Results exclude the current user.
     */
    suspend fun searchUsers(query: String): List<UserProfile> {
        if (query.isBlank()) return emptyList()
        val lower = query.lowercase().trim()
        return try {
            val snapshot = db.collection(USERS_COL)
                .whereGreaterThanOrEqualTo("searchName", lower)
                .whereLessThan("searchName", lower + "\uF8FF")
                .limit(15)
                .get().await()

            snapshot.documents.mapNotNull {
                it.toObject(UserProfile::class.java)
            }.filter { it.uid != myUid }  // exclude self
        } catch (e: Exception) {
            Log.e(TAG, "User search failed for '$query'", e)
            emptyList()
        }
    }

    /**
     * Fetches a [UserProfile] by Firebase UID.
     */
    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            db.collection(USERS_COL).document(uid).get().await()
                .toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch profile for $uid", e)
            null
        }
    }
}
