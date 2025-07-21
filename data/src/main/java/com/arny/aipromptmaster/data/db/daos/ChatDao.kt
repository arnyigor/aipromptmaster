package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.domain.models.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- Операции с Диалогами ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    // --- Операции с Сообщениями ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Получает реактивный поток сообщений для КОНКРЕТНОГО диалога.
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    /**
     * Очищает все сообщения для КОНКРЕТНОГО диалога.
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessagesForConversation(conversationId: String)

    /**
     * Получает список всех диалогов, обогащенный последним сообщением.
     * Этот запрос использует подзапрос для нахождения последнего сообщения
     * для каждого диалога и сортирует результат по времени обновления диалога.
     */
    @Query("""
        SELECT 
            c.id, 
            c.title AS name, 
            (SELECT content FROM messages WHERE conversationId = c.id ORDER BY timestamp DESC LIMIT 1) AS lastMessage, 
            c.lastUpdated AS timestamp
        FROM conversations c
        ORDER BY c.lastUpdated DESC
    """)
    fun getChatList(): Flow<List<Chat>> // Room сам смаппит результат в data class Chat

    @Query("UPDATE conversations SET lastUpdated = :timestamp WHERE id = :conversationId")
    suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long)
}
