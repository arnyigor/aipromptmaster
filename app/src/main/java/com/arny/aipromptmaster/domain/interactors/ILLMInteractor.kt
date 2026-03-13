package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Интерфейс для взаимодействия с бизнес-логикой LLM.
 */
interface ILLMInteractor {

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
    suspend fun setSystemPromptWithChatCreation(
        conversationId: String?,
        prompt: String,
        chatTitle: String = "Чат с системным промптом"
    ): String

    /**
     * Получает системный промпт для указанного диалога.
     *
     * @param conversationId ID диалога.
     * @return Текст системного промпта или null, если он не задан.
     */
    suspend fun getSystemPrompt(conversationId: String): String

    suspend fun deleteConversation(conversationId: String)

    /**
     * Получает полную историю чата для экспорта.
     *
     * @param conversationId ID диалога.
     * @return Отформатированная строка с полной историей чата.
     */
    suspend fun getFullChatForExport(conversationId: String): String

    /**
     * Отменяет текущий активный запрос к LLM
     */
    fun cancelCurrentRequest()

    /**
     * Отправляет сообщение пользователя и получает ответ от LLM.
     *
     * @param conversationId ID диалога.
     * @param userMessageText Текст сообщения пользователя.
     * @param attachedFiles Список прикрепленных файлов.
     * @param skipUserMessageCreation Если true, не создает сообщение пользователя в БД
     *                                (используется для retry/regenerate).
     */
    suspend fun sendMessageWithFallback(
        conversationId: String?,
        userMessageText: String,
        attachedFiles: List<FileAttachment> = emptyList(),
        skipUserMessageCreation: Boolean = false
    )

    fun observeStreamingBuffer(): StateFlow<Map<String, String>>

    /**
     * Оценка количества токенов для текущего ввода + файлов + системного промпта
     * и последних 10 сообщений из истории.
     */
   suspend fun estimateTokens(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        systemPrompt: String?,
        recentMessages: List<ChatMessage>
    ): Int

    suspend fun deleteMessage(id: String)
}