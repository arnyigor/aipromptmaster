package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.TokenEstimationResult
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
     * ✅ УПРОЩЕННЫЙ метод отправки сообщения
     * Файлы автоматически подтягиваются из чата
     *
     * @param model Модель языка, которую нужно использовать.
     * @param conversationId ID диалога.
     * @param estimatedTokens Предполагаемое количество токенов.
     */
    suspend fun sendMessage(
        model: String,
        conversationId: String,
        estimatedTokens: Int
    )

    /**
     * Устанавливает системный промпт для указанного диалога.
     *
     * @param conversationId ID диалога.
     * @param prompt Текст системного промпта.
     */
    suspend fun setSystemPrompt(conversationId: String, prompt: String)

    /**
     * Устанавливает системный промпт для диалога, создавая новый диалог при необходимости.
     *
     * @param conversationId ID диалога (может быть null для создания нового).
     * @param prompt Текст системного промпта.
     * @param chatTitle Название чата (используется при создании нового диалога).
     * @return ID диалога, для которого был установлен промпт.
     */
    suspend fun setSystemPromptWithChatCreation(conversationId: String?, prompt: String, chatTitle: String = "Чат с системным промптом"): String

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

    /**
     * Отменяет текущий активный запрос к LLM
     */
    fun cancelCurrentRequest()

    /**
     * Регенерирует последний ответ ассистента
     * Удаляет последний ответ и отправляет запрос заново
     * @param model Модель языка
     * @param conversationId ID диалога
     */
    suspend fun regenerateLastResponse(
        model: String,
        conversationId: String
    )

    /**
     * Регенерация ЛЮБОГО сообщения в истории
     * @param model Модель языка
     * @param conversationId ID диалога
     * @param messageId ID сообщения которое нужно регенерировать
     */
    suspend fun regenerateMessage(
        model: String,
        conversationId: String,
        messageId: String
    )

    /**
     * Добавить файл к чату
     * @param conversationId ID чата
     * @param file Файл для добавления
     */
    suspend fun addFileToConversation(conversationId: String, file: FileAttachment)

    /**
     * Удалить файл из чата
     * @param conversationId ID чата
     * @param fileId ID файла для удаления
     */
    suspend fun removeFileFromConversation(conversationId: String, fileId: String)

    /**
     * Получить файлы чата
     * @param conversationId ID чата
     * @return Список файлов чата
     */
    suspend fun getConversationFiles(conversationId: String): List<FileAttachment>

    /**
     * Получить поток файлов чата
     * @param conversationId ID чата
     * @return Поток списка файлов чата
     */
    fun getConversationFilesFlow(conversationId: String): Flow<List<FileAttachment>>


    /**
     * Добавляет пустое сообщение ассистента для streaming
     * @return ID созданного сообщения
     */
    suspend fun createAssistantMessage(conversationId: String): String

    suspend fun estimateTokensForCurrentChat(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        conversationId: String?,
    ): TokenEstimationResult
}