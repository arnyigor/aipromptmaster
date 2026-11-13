package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface IChatHistoryRepository {
    /**
     * Предоставляет реактивный поток с полной историей сообщений.
     */
    fun getHistoryFlow(): Flow<List<ChatMessage>>

    /**
     * Добавляет одно сообщение в историю.
     */
    suspend fun addMessage(message: ChatMessage)

    /**
     * Обновляет существующее сообщение.
     */
    suspend fun updateMessage(message: ChatMessage)

    /**
     * Удаляет сообщение по ID.
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Получает сообщение по ID.
     */
    suspend fun getMessage(messageId: String): ChatMessage?

    /**
     * Добавляет несколько сообщений в историю (для обратной совместимости).
     */
    suspend fun addMessages(messages: List<ChatMessage>) {
        messages.forEach { addMessage(it) }
    }

    /**
     * Очищает историю чата.
     */
    suspend fun clearHistory()
}