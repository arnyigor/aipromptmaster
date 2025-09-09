package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс для взаимодействия с бизнес-логикой LLM.
 */
interface ILLMInteractor {

    /**
     * Возвращает поток списка доступных моделей языка.
     *
     * @return Поток списка моделей языка [LlmModel], завернутый в [DataResult].
     */
    fun getModels(): Flow<DataResult<List<LlmModel>>>

    /**
     * Возвращает поток выбранной модели языка.
     *
     * @return Поток выбранной модели языка [LlmModel], завернутый в [DataResult].
     */
    fun getSelectedModel(): Flow<DataResult<LlmModel>>

    /**
     * Выбирает модель языка по ее ID.
     *
     * @param id ID модели языка.
     */
    suspend fun selectModel(id: String)

    /**
     * Обновляет список доступных моделей языка.
     *
     * @return Результат обновления, завернутый в [Result].
     */
    suspend fun refreshModels(): Result<Unit>

    /**
     * Переключает выбранную модель языка.
     *
     * @param clickedModelId ID模型 языка, на которую нужно переключиться.
     */
    suspend fun toggleModelSelection(clickedModelId: String)

    /**
     * Возвращает поток истории сообщений для указанного диалога.
     *
     * @param conversationId ID диалога.
     * @return Поток списка сообщений [ChatMessage].
     */
    fun getChatHistoryFlow(conversationId: String?): Flow<List<ChatMessage>>

    /**
     * Отправляет сообщение в указанный диалог, используя указанную модель языка.
     *
     * @param model Модель языка, которую нужно использовать.
     * @param conversationId ID диалога.
     * @return Поток результатов отправки сообщения, завернутый в [DataResult].
     */
    fun sendMessage(model: String, conversationId: String?): Flow<DataResult<String>>

    /**
     * Очищает историю указанного диалога.
     *
     * @param conversationId ID диалога.
     */
    suspend fun clearChat(conversationId: String?)

    /**
     * Возвращает поток списка доступных чатов.
     *
     * @return Поток списка чатов [Chat].
     */
    fun getChatList(): Flow<List<Chat>>

    /**
     * Добавляет сообщение пользователя в историю указанного диалога.
     *
     * @param conversationId ID диалога.
     * @param userMessage Сообщение пользователя.
     */
    suspend fun addUserMessageToHistory(conversationId: String, userMessage: String)

    /**
     * Добавляет сообщение помощника в историю указанного диалога.
     *
     * @param conversationId ID диалога.
     * @param assistantMessage Сообщение помощника.
     */
    suspend fun addAssistantMessageToHistory(conversationId: String, assistantMessage: String)

    /**
     * Создает новый диалог и возвращает его ID.
     *
     * @param title Название нового диалога.
     * @return ID нового диалога.
     */
    suspend fun createNewConversation(title: String): String

    /**
     * Отправляет сообщение в указанный диалог, используя указанную модель языка, с использованием резервного метода.
     *
     * @param model Модель языка, которую нужно использовать.
     * @param conversationId ID диалога.
     */
    suspend fun sendMessageWithFallback(model: String, conversationId: String?)

    /**
     * Устанавливает системный промпт для указанного диалога.
     *
     * @param conversationId ID диалога.
     * @param prompt Текст системного промпта.
     */
    suspend fun setSystemPrompt(conversationId: String, prompt: String)

    /**
     * Получает системный промпт для указанного диалога.
     *
     * @param conversationId ID диалога.
     * @return Текст системного промпта или null, если он не задан.
     */
    suspend fun getSystemPrompt(conversationId: String): String?

    suspend fun deleteConversation(conversationId: String)

    /**
     * Получает полную историю чата для экспорта.
     *
     * @param conversationId ID диалога.
     * @return Отформатированная строка с полной историей чата.
     */
    suspend fun getFullChatForExport(conversationId: String): String

    suspend fun toggleModelFavorite(modelId: String)

    fun getFavoriteModels(): Flow<List<LlmModel>>
}