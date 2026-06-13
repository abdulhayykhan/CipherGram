package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM chat_threads ORDER BY lastMessageTime DESC")
    fun getThreadsFlow(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun getThreadById(id: String): ChatThread?

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThreadFlow(threadId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_threads SET lastMessageText = :text, lastMessageTime = :time WHERE id = :threadId")
    suspend fun updateThreadLastMessage(threadId: String, text: String, time: Long)

    @Query("UPDATE chat_messages SET isScrapedMedia = :isScraped, mediaImageUrl = :imageUrl, mediaVideoUrl = :videoUrl, mediaCaption = :caption WHERE id = :id")
    suspend fun updateMessageMedia(id: Long, isScraped: Boolean, imageUrl: String?, videoUrl: String?, caption: String?)

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun deleteThreadById(id: String)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)
}
