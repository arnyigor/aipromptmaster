package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface IChatHistoryRepository {
    /**
     * Предоставляет реактивный поток с полной историей сообщений.
     */
    fun getHistoryFlow(): Flow<List<ChatMessage>>

    /**
     * Добавляет одно или несколько сообщений в историю.
     */
    suspend fun addMessages(messages: List<ChatMessage>)

    /**
     * Очищает историю чата.
     */
    suspend fun clearHistory()
}