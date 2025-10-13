package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.domain.models.Chat
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object для работы с данными чатов и сообщений в локальной базе данных.
 */
@Dao
interface ChatDao {
    // --- Операции с Диалогами ---

    /**
     * Вставляет или заменяет диалог в базе данных.
     *
     * @param conversation Объект диалога для вставки/замены.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    // --- Операции с Сообщениями ---

    /**
     * Вставляет список сообщений в базу данных, заменяя существующие при конфликте.
     *
     * @param messages Список сообщений для вставки.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Вставляет одно сообщение в базу данных, заменяя существующее при конфликте.
     *
     * @param message Объект сообщения для вставки.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * Обновляет содержимое сообщения по его идентификатору.
     *
     * @param messageId Идентификатор сообщения для обновления.
     * @param newContent Новое содержимое сообщения.
     */
    @Query("UPDATE messages SET content = :newContent WHERE conversationId = :messageId")
    suspend fun updateMessageContent(messageId: String, newContent: String)

    /**
     * Добавляет часть содержимого к существующему сообщению.
     *
     * @param messageId Идентификатор сообщения.
     * @param contentChunk Часть содержимого для добавления.
     */
    @Query("UPDATE messages SET content = content || :contentChunk WHERE conversationId = :messageId")
    suspend fun appendContentToMessage(messageId: String, contentChunk: String)

    /**
     * Получает реактивный поток сообщений для конкретного диалога, отсортированных по времени.
     *
     * @param conversationId Идентификатор диалога.
     * @return Поток списка сообщений, отсортированных по возрастанию времени создания.
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    /**
     * Очищает все сообщения для конкретного диалога.
     *
     * @param conversationId Идентификатор диалога, сообщения которого нужно удалить.
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessagesForConversation(conversationId: String)

    /**
     * Получает список всех диалогов, обогащенный последним сообщением.
     * Этот запрос использует подзапрос для нахождения последнего сообщения
     * для каждого диалога и сортирует результат по времени обновления диалога.
     *
     * @return Поток списка диалогов с последними сообщениями, отсортированных по времени обновления.
     */
    @Query("""
        SELECT 
            c.conversationId, 
            c.title AS name, 
            (SELECT content FROM messages WHERE conversationId = c.conversationId ORDER BY timestamp DESC LIMIT 1) AS lastMessage, 
            c.lastUpdated AS timestamp
        FROM conversations c
        ORDER BY c.lastUpdated DESC
    """)
    fun getChatList(): Flow<List<Chat>> // Room сам смаппит результат в data class Chat

    /**
     * Обновляет временную метку последнего обновления диалога.
     *
     * @param conversationId Идентификатор диалога.
     * @param timestamp Новая временная метка обновления.
     */
    @Query("UPDATE conversations SET lastUpdated = :timestamp WHERE conversationId = :conversationId")
    suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long)

    /**
     * Удаляет сообщение по его первичному ключу.
     *
     * @param messageId ID сообщения для удаления.
     */
    @Query("DELETE FROM messages WHERE conversationId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /**
     * Обновляет системный промпт для указанного диалога.
     *
     * @param conversationId Идентификатор диалога.
     * @param prompt Новый текст системного промпта.
     */
    @Query("UPDATE conversations SET systemPrompt = :prompt WHERE conversationId = :conversationId")
    suspend fun updateSystemPrompt(conversationId: String, prompt: String)

    /**
     * Получает системный промпт для указанного диалога.
     *
     * @param conversationId Идентификатор диалога.
     * @return Текст системного промпта или null, если он не задан.
     */
    @Query("SELECT systemPrompt FROM conversations WHERE conversationId = :conversationId")
    suspend fun getSystemPrompt(conversationId: String): String?

    /**
     * Удаляет диалог по его ID.
     * Все связанные сообщения будут удалены автоматически благодаря onDelete = CASCADE.
     *
     * @param conversationId Идентификатор диалога для удаления.
     */
    @Query("DELETE FROM conversations WHERE conversationId = :conversationId")
    suspend fun deleteConversationById(conversationId: String)

    /**
     * Получает один диалог по его ID.
     */
    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity? // Nullable на случай неверного ID

    /**
     * Получает ВСЕ сообщения для диалога, отсортированные по времени.
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForConversation(conversationId: String): List<MessageEntity>
}