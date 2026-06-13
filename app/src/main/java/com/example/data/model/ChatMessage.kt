package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val senderId: String,
    val text: String, // Can be plain raw text, URL, or cipher block like ENC:[base64]
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val isSender: Boolean = true,
    
    // Extracted Instagram Media Preview Fields
    val isScrapedMedia: Boolean = false,
    val mediaImageUrl: String? = null,
    val mediaVideoUrl: String? = null,
    val mediaCaption: String? = null
)
