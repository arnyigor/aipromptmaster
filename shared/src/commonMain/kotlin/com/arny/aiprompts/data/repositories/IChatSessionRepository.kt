package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс репозитория для работы с сессиями чата.
 * Предоставляет методы для управления сессиями и сообщениями.
 */
interface IChatSessionRepository {

    /**
     * Получает поток всех активных (не архивированных) сессий.
     * Сессии отсортированы по времени обновления (новые сверху).
     */
    fun getAllSessions(): Flow<List<ChatSession>>

    /**
     * Получает сессию по ID в виде потока.
     * При изменении сессии в БД поток автоматически обновится.
     */
    fun getSessionById(sessionId: String): Flow<ChatSession?>

    /**
     * Создает новую сессию чата с указанными параметрами.
     *
     * @param name Название сессии
     * @param systemPrompt System prompt (опционально)
     * @return Созданная сессия
     */
    suspend fun createSession(name: String, systemPrompt: String? = null): ChatSession

    /**
     * Обновляет существующую сессию.
     *
     * @param session Обновленная сессия
     */
    suspend fun updateSession(session: ChatSession)

    /**
     * Обновляет только название сессии.
     *
     * @param sessionId ID сессии
     * @param newName Новое название
     */
    suspend fun renameSession(sessionId: String, newName: String)

    /**
     * Удаляет сессию и все её сообщения.
     *
     * @param sessionId ID сессии для удаления
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Архивирует сессию (мягкое удаление).
     * Сессия скрывается из списка но остается в БД.
     *
     * @param sessionId ID сессии для архивирования
     */
    suspend fun archiveSession(sessionId: String)

    /**
     * Восстанавливает сессию из архива.
     *
     * @param sessionId ID сессии для восстановления
     */
    suspend fun unarchiveSession(sessionId: String)

    /**
     * Получает поток сообщений для указанной сессии.
     * Сообщения отсортированы по порядку добавления.
     *
     * @param sessionId ID сессии
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Добавляет сообщение в сессию.
     *
     * @param sessionId ID сессии
     * @param message Сообщение для добавления
     */
    suspend fun addMessage(sessionId: String, message: ChatMessage)

    /**
     * Обновляет существующее сообщение.
     *
     * @param message Обновленное сообщение
     */
    suspend fun updateMessage(message: ChatMessage)

    /**
     * Удаляет сообщение по ID.
     *
     * @param messageId ID сообщения для удаления
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Очищает все сообщения в сессии.
     *
     * @param sessionId ID сессии
     */
    suspend fun clearSessionMessages(sessionId: String)

    /**
     * Получает последние N сообщений для формирования контекста API.
     *
     * @param sessionId ID сессии
     * @param limit Количество сообщений
     */
    suspend fun getRecentMessagesForContext(sessionId: String, limit: Int): List<ChatMessage>

    /**
     * Обновляет system prompt для сессии.
     *
     * @param sessionId ID сессии
     * @param systemPrompt Новый system prompt
     */
    suspend fun updateSystemPrompt(sessionId: String, systemPrompt: String?)

    /**
     * Поиск сессий по названию.
     *
     * @param query Поисковый запрос
     */
    fun searchSessions(query: String): Flow<List<ChatSession>>
}
