package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс для взаимодействия с репозиторием истории чата.
 */
interface IChatHistoryRepository {

    /**
     * Получает диалог по его идентификатору.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @return Объект диалога [Conversation] или null, если диалог не найден.
     */
    suspend fun getConversation(conversationId: String): Conversation?

    /**
     * Получает полную историю сообщений для указанного диалога.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @return Список всех сообщений диалога [ChatMessage].
     */
    suspend fun getFullHistory(conversationId: String): List<ChatMessage>

    /**
     * Возвращает поток истории сообщений для указанного диалога.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @return Поток списков сообщений [ChatMessage].
     */
    fun getHistoryFlow(conversationId: String): Flow<List<ChatMessage>>

    /**
     * Добавляет список сообщений к указанному диалогу.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @param messages Список сообщений [ChatMessage] для добавления.
     */
    suspend fun addMessages(conversationId: String, messages: List<ChatMessage>)

    /**
     * Очищает историю указанного диалога.
     *
     * @param conversationId Уникальный идентификатор диалога.
     */
    suspend fun clearHistory(conversationId: String)

    /**
     * Создает новый диалог в базе данных и возвращает его уникальный идентификатор.
     *
     * @param title Название нового диалога.
     * @return Уникальный идентификатор созданного диалога.
     */
    suspend fun createNewConversation(title: String): String

    /**
     * Возвращает поток списка доступных чатов.
     *
     * @return Поток списков чатов [Chat].
     */
    fun getChatList(): Flow<List<Chat>>

    /**
     * Добавляет часть содержимого к существующему сообщению.
     *
     * @param messageId Уникальный идентификатор сообщения.
     * @param contentChunk Часть содержимого для добавления.
     */
    suspend fun appendContentToMessage(messageId: String, contentChunk: String)

    /**
     * Добавляет новое сообщение к указанному диалогу и возвращает его уникальный идентификатор.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @param message Новое сообщение [ChatMessage] для добавления.
     * @return Уникальный идентификатор добавленного сообщения.
     */
    suspend fun addMessage(conversationId: String, message: ChatMessage): String

    /**
     * Обновляет содержимое существующего сообщения.
     *
     * @param messageId Уникальный идентификатор сообщения.
     * @param newContent Новое содержимое сообщения.
     */
    suspend fun updateMessageContent(messageId: String, newContent: String)

    /**
     * Удаляет одно конкретное сообщение из истории по его уникальному идентификатору.
     *
     * @param messageId Уникальный идентификатор сообщения для удаления.
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Возвращает системный текст подсказки для указанного диалога.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @return Системный текст подсказки или null, если его нет.
     */
    suspend fun getSystemPrompt(conversationId: String): String?

    /**
     * Обновляет системный текст подсказки для указанного диалога.
     *
     * @param conversationId Уникальный идентификатор диалога.
     * @param prompt Новый системный текст подсказки.
     */
    suspend fun updateSystemPrompt(conversationId: String, prompt: String)

    /**
     * Удаляет диалог и всю связанную с ним историю.
     *
     * @param conversationId Уникальный идентификатор диалога для удаления.
     */
    suspend fun deleteConversation(conversationId: String)
}