package com.example.data.model

/**
 * Represents a single chat message stored in Firestore.
 * Collection path: /threads/{threadId}/messages/{messageId}
 *
 * The [encryptedText] field always holds AES-GCM ciphertext (prefix "ENC:")
 * derived from the ECDH shared secret between the two participants.
 */
data class ChatMessage(
    val id: String = "",
    val threadId: String = "",
    val senderId: String = "",          // Firebase UID of the sender
    val senderUsername: String = "",    // Display name for UI rendering
    val encryptedText: String = "",     // Always E2EE ciphertext: "ENC:base64..."
    val timestamp: Long = 0L,
    val isSender: Boolean = false,      // True if this message was sent by the current user

    // Instagram media preview fields (populated after scraping)
    val isScrapedMedia: Boolean = false,
    val mediaImageUrl: String? = null,
    val mediaVideoUrl: String? = null,
    val mediaCaption: String? = null,

    // Attachment fields (Firebase Storage reference)
    val attachmentRef: String? = null,  // Firebase Storage path
    val attachmentType: String? = null  // "img", "voice", "vid"
)
