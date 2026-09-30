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
