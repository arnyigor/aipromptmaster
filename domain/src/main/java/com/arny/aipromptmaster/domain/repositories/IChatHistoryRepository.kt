package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import kotlinx.coroutines.flow.Flow

interface IChatHistoryRepository {
    // Теперь метод требует ID диалога
    fun getHistoryFlow(conversationId: String): Flow<List<ChatMessage>>

    // Добавляет сообщения в конкретный диалог
    suspend fun addMessages(conversationId: String, messages: List<ChatMessage>)

    // Очищает конкретный диалог
    suspend fun clearHistory(conversationId: String)

    // Создает новый диалог в базе и возвращает его ID
    suspend fun createNewConversation(title: String): String

    fun getChatList(): Flow<List<Chat>>
}
