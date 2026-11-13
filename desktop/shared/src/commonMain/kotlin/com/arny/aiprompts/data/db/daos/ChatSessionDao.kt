package com.arny.aiprompts.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arny.aiprompts.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с сессиями чата.
 * Предоставляет методы для CRUD операций с таблицей chat_sessions.
 */
@Dao
interface ChatSessionDao {
    @Query("SELECT s.*, (SELECT content FROM chat_messages WHERE session_id=s.id ORDER BY order_index DESC LIMIT 1) AS lastContent, (SELECT COALESCE(SUM(token_count),0) FROM chat_messages WHERE session_id=s.id) AS totalTokens FROM chat_sessions s ORDER BY s.updated_at DESC")
    fun summaries(): Flow<List<ChatSessionSummary>>


    /**
     * Получает все активные (не архивированные) сессии, отсортированные по времени обновления.
     * Использует Flow для реактивных обновлений UI.
     */
    @Query("SELECT * FROM chat_sessions WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun getAllActiveSessions(): Flow<List<ChatSessionEntity>>

    /**
     * Получает все сессии включая архивированные.
     */
    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    /**
     * Получает сессию по ID.
     * Возвращает null если сессия не найдена.
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    /**
     * Вставляет новую сессию или обновляет существующую (при конфликте ID).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    /**
     * Обновляет существующую сессию.
     */
    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET name = :name, updated_at = :timestamp WHERE id = :id")
    suspend fun rename(id: String, name: String, timestamp: Long)
    @Query("UPDATE chat_sessions SET system_prompt = :prompt, updated_at = :timestamp WHERE id = :id")
    suspend fun setSystemPrompt(id: String, prompt: String?, timestamp: Long)
    @Query("UPDATE chat_sessions SET temperature = :temperature, max_tokens = :maxTokens, top_p = :topP, context_window = :contextWindow, updated_at = :timestamp WHERE id = :id")
    suspend fun setSettings(id: String, temperature: Float, maxTokens: Int, topP: Float, contextWindow: Int, timestamp: Long)
    @Query("UPDATE chat_sessions SET model_id = :modelId, provider_id = :providerId, updated_at = :timestamp WHERE id = :id")
    suspend fun setModel(id: String, modelId: String, providerId: String?, timestamp: Long)

    /**
     * Удаляет сессию из базы данных.
     * Все сообщения сессии будут удалены автоматически (CASCADE).
     */
    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    /**
     * Архивирует сессию (мягкое удаление).
     * Сессия скрывается из списка активных, но остается в БД.
     */
    @Query("UPDATE chat_sessions SET is_archived = 1, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun archiveSession(sessionId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Восстанавливает сессию из архива.
     */
    @Query("UPDATE chat_sessions SET is_archived = 0, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun unarchiveSession(sessionId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Обновляет время последнего изменения сессии.
     */
    @Query("UPDATE chat_sessions SET updated_at = :timestamp WHERE id = :sessionId")
    suspend fun updateTimestamp(sessionId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Поиск сессий по названию (для функции поиска).
     */
    @Query("SELECT * FROM chat_sessions WHERE name LIKE '%' || :query || '%' AND is_archived = 0 ORDER BY updated_at DESC")
    fun searchSessions(query: String): Flow<List<ChatSessionEntity>>

    /**
     * Получает количество сессий в базе.
     */
    @Query("SELECT COUNT(*) FROM chat_sessions WHERE is_archived = 0")
    suspend fun getActiveSessionCount(): Int

    /**
     * Удаляет все архивированные сессии старше указанного времени.
     * Используется для очистки старых данных.
     */
    @Query("DELETE FROM chat_sessions WHERE is_archived = 1 AND updated_at < :timestamp")
    suspend fun deleteOldArchivedSessions(timestamp: Long)
}

data class ChatSessionSummary(@androidx.room.Embedded val session: ChatSessionEntity, val lastContent: String?, val totalTokens: Int)
