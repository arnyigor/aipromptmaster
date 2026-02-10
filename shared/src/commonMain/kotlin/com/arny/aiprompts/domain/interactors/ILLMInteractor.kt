package com.arny.aiprompts.domain.interactors

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс интерактора для работы с LLM.
 * Объединяет работу с моделями, сессиями чата и генерацией ответов.
 */
interface ILLMInteractor {

    // ==================== Модели ====================

    /**
     * Получает поток со списком всех доступных моделей.
     */
    fun getModels(): Flow<DataResult<List<LlmModel>>>

    /**
     * Получает поток с выбранной моделью.
     */
    fun getSelectedModel(): Flow<DataResult<LlmModel>>

    /**
     * Выбирает модель по ID.
     */
    suspend fun selectModel(id: String)

    /**
     * Обновляет список моделей из API.
     */
    suspend fun refreshModels(): Result<Unit>

    /**
     * Переключает выбор модели (выбирает/снимает выбор).
     */
    suspend fun toggleModelSelection(clickedModelId: String)

    // ==================== Сессии чата ====================

    /**
     * Получает поток всех активных сессий чата.
     */
    fun getAllSessions(): Flow<List<ChatSession>>

    /**
     * Получает сессию по ID.
     */
    fun getSessionById(sessionId: String): Flow<ChatSession?>

    /**
     * Создает новую сессию чата.
     *
     * @param name Название сессии
     * @param systemPrompt System prompt (опционально)
     * @return Созданная сессия
     */
    suspend fun createSession(name: String, systemPrompt: String? = null): ChatSession

    /**
     * Удаляет сессию чата.
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Архивирует сессию чата.
     */
    suspend fun archiveSession(sessionId: String)

    /**
     * Переименовывает сессию чата.
     */
    suspend fun renameSession(sessionId: String, newName: String)

    /**
     * Обновляет system prompt для сессии.
     */
    suspend fun updateSystemPrompt(sessionId: String, systemPrompt: String?)

    /**
     * Обновляет настройки чата для сессии.
     */
    suspend fun updateChatSettings(sessionId: String, settings: ChatSettings)

    // ==================== Сообщения ====================

    /**
     * Отправляет сообщение в сессию и получает потоковый ответ от модели.
     *
     * @param sessionId ID сессии
     * @param content Текст сообщения
     * @return Поток с частями ответа
     */
    fun sendMessage(sessionId: String, content: String): Flow<DataResult<ChatMessage>>

    /**
     * Получает поток сообщений для сессии.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Получает историю чата для глобальной сессии (для обратной совместимости).
     * @deprecated Используйте getMessagesForSession
     */
    fun getChatHistoryFlow(): Flow<List<ChatMessage>>

    /**
     * Очищает историю сообщений в сессии.
     */
    suspend fun clearChat(sessionId: String? = null)

    /**
     * Удаляет сообщение по ID.
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Повторяет отправку сообщения, которое завершилось ошибкой.
     */
    suspend fun retryMessage(messageId: String)

    /**
     * Редактирует сообщение пользователя и перегенерирует ответ.
     */
    suspend fun editMessage(messageId: String, newContent: String): Flow<DataResult<ChatMessage>>

    /**
     * Отменяет текущую потоковую генерацию.
     */
    suspend fun cancelStreaming()
}
