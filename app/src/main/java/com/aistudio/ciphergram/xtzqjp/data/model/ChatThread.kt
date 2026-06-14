package com.aistudio.ciphergram.xtzqjp.data.model

/**
 * Represents a chat thread between exactly two users.
 * Collection path: /threads/{threadId}
 *
 * [id] is deterministic: sorted(uid1, uid2).joinToString("_")
 * This ensures both users refer to the same thread document.
 */
data class ChatThread(
    val id: String = "",
    val participants: List<String> = emptyList(),       // [uid1, uid2]
    val participantNames: Map<String, String> = emptyMap(),  // uid -> displayName
    val participantPics: Map<String, String> = emptyMap(),   // uid -> profilePicUrl
    val lastMessage: String = "",      // Preview text (always "[Encrypted]" for E2EE messages)
    val lastMessageTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns the other participant's UID given the current user's UID. */
    fun getOtherParticipantId(myUid: String): String {
        return participants.firstOrNull { it != myUid } ?: ""
    }

    /** Returns the display name of the other participant. */
    fun getContactName(myUid: String): String {
        val otherId = getOtherParticipantId(myUid)
        return participantNames[otherId] ?: "Unknown"
    }

    /** Returns the profile picture URL of the other participant. */
    fun getContactPic(myUid: String): String {
        val otherId = getOtherParticipantId(myUid)
        return participantPics[otherId] ?: ""
    }
}
