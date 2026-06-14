package com.aistudio.ciphergram.xtzqjp.data.model

data class MockMessageItem(
    val id: String,
    val text: String,
    val senderId: String,
    val isSender: Boolean,
    val voiceMessagePath: String? = null,
    val videoUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
