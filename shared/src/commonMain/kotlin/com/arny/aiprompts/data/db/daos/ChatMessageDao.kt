package com.arny.aiprompts.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arny.aiprompts.data.db.entities.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с сообщениями чата.
 * Предоставляет методы для CRUD операций с таблицей chat_messages.
 */
@Dao
interface ChatMessageDao {

    /**
     * Получает все сообщения для указанной сессии, отсортированные по порядку.
     * Использует Flow для реактивных обновлений UI.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY order_index ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Получает сообщение по ID.
     * Возвращает null если сообщение не найдено.
     */
    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    /**
     * Вставляет новое сообщение или обновляет существующее (при конфликте ID).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Вставляет несколько сообщений за один запрос.
     * Эффективнее чем множественные insertMessage.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * Обновляет существующее сообщение.
     */
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * Удаляет сообщение из базы данных.
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    /**
     * Удаляет сообщение по ID.
     */
    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /**
     * Удаляет все сообщения для указанной сессии.
     * Используется при очистке чата.
     */
    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    /**
     * Получает максимальный order_index для сессии.
     * Используется для добавления нового сообщения в конец.
     * Возвращает null если в сессии нет сообщений.
     */
    @Query("SELECT MAX(order_index) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMaxOrderIndex(sessionId: String): Int?

    /**
     * Получает последние N сообщений для сессии.
     * Используется для формирования контекста API запроса.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY order_index DESC LIMIT :limit")
    suspend fun getLastMessages(sessionId: String, limit: Int): List<ChatMessageEntity>

    /**
     * Получает количество сообщений в сессии.
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * Получает общее количество токенов в сессии.
     * Полезно для отображения статистики.
     */
    @Query("SELECT SUM(token_count) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getTotalTokenCount(sessionId: String): Int?

    /**
     * Обновляет статус сообщения.
     * Используется при streaming для обновления прогресса.
     */
    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    /**
     * Обновляет контент сообщения.
     * Используется при streaming для добавления новых частей текста.
     */
    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)

    /**
     * Поиск сообщений по содержимому.
     * Используется для функции поиска по истории чата.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId AND content LIKE '%' || :query || '%' ORDER BY order_index ASC")
    suspend fun searchMessagesInSession(sessionId: String, query: String): List<ChatMessageEntity>

    /**
     * Получает последнее сообщение в сессии.
     * Используется для отображения preview в списке чатов.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY order_index DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: String): ChatMessageEntity?
}
