package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey val id: String,
    val contactName: String,
    val contactUsername: String,
    val profilePicUrl: String,
    val sharedSecret: String, // Dynamic passphrase for encrypting this chat
    val lastMessageText: String = "",
    val lastMessageTime: Long = System.currentTimeMillis()
)
