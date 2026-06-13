package com.example.data.repository

import android.util.Log
import com.example.data.local.MessageDao
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.network.InstagramScraper
import com.example.data.network.SessionManager
import com.example.security.CryptoEngine
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val messageDao: MessageDao,
    private val sessionManager: SessionManager
) {
    val threadsFlow: Flow<List<ChatThread>> = messageDao.getThreadsFlow()

    fun getMessagesForThread(threadId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForThreadFlow(threadId)
    }

    suspend fun getThreadById(id: String): ChatThread? {
        return messageDao.getThreadById(id)
    }

    suspend fun createThread(
        id: String,
        contactName: String,
        contactUsername: String,
        profilePicUrl: String,
        sharedSecret: String
    ) {
        val finalProfileUrl = profilePicUrl.ifEmpty { 
            "https://picsum.photos/seed/${id}/150/150" 
        }
        val thread = ChatThread(
            id = id,
            contactName = contactName,
            contactUsername = contactUsername,
            profilePicUrl = finalProfileUrl,
            sharedSecret = sharedSecret,
            lastMessageText = "Secure E2EE Channel Established"
        )
        messageDao.insertThread(thread)
    }

    suspend fun createMessage(
        threadId: String,
        senderId: String,
        rawText: String,
        isSender: Boolean,
        encryptMode: Boolean
    ): Long {
        val thread = messageDao.getThreadById(threadId)
        val sharedSecret = thread?.sharedSecret ?: ""
        
        val isEncrypted = encryptMode && (sharedSecret.isNotEmpty() || sessionManager.isSandboxMode())
        val finalPayload = if (isEncrypted) {
            if (sessionManager.isSandboxMode()) {
                com.example.security.LocalCryptoEngine.encrypt(rawText)
            } else {
                CryptoEngine.encrypt(rawText, sharedSecret)
            }
        } else {
            rawText
        }

        val isMedia = InstagramScraper.isInstagramUrl(rawText)

        val message = ChatMessage(
            threadId = threadId,
            senderId = senderId,
            text = finalPayload,
            timestamp = System.currentTimeMillis(),
            isEncrypted = isEncrypted,
            isSender = isSender,
            isScrapedMedia = isMedia
        )

        val msgId = messageDao.insertMessage(message)
        
        // Update thread preview
        val previewText = if (isEncrypted) "[Encrypted Message]" else rawText
        messageDao.updateThreadLastMessage(threadId, previewText, message.timestamp)

        // If it features Instagram Media, trigger crawl
        if (isMedia) {
            triggerMediaScrape(msgId, rawText)
        }

        return msgId
    }

    suspend fun triggerMediaScrape(messageId: Long, urlString: String) {
        try {
            val cookies = sessionManager.getCookies()
            val userAgent = sessionManager.getUserAgent()
            
            // Synchronize interceptor parameters
            InstagramScraper.updateScraperSession(userAgent, cookies)
            
            val result = InstagramScraper.scrapeMedia(urlString, cookies)
            messageDao.updateMessageMedia(
                id = messageId,
                isScraped = true,
                imageUrl = result.imageUrl,
                videoUrl = result.videoUrl,
                caption = result.caption
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Media scrape action failed for message $messageId", e)
        }
    }

    suspend fun deleteThread(threadId: String) {
        messageDao.deleteThreadById(threadId)
        messageDao.deleteMessagesForThread(threadId)
    }
}
