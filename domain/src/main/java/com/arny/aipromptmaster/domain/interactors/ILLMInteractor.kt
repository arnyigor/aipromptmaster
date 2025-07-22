package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow

interface ILLMInteractor {
    fun getModels(): Flow<DataResult<List<LlmModel>>>
    fun getSelectedModel(): Flow<DataResult<LlmModel>>
    suspend fun selectModel(id: String)
    suspend fun refreshModels(): Result<Unit>
    suspend fun toggleModelSelection(clickedModelId: String)
    fun getChatHistoryFlow(conversationId: String?): Flow<List<ChatMessage>>
    fun sendMessage(model: String, conversationId: String?): Flow<DataResult<String>>
    suspend fun clearChat(conversationId: String?)
    fun getChatList(): Flow<List<Chat>>
    suspend fun addUserMessageToHistory(conversationId: String, userMessage: String)
    suspend fun addAssistantMessageToHistory(conversationId: String, assistantMessage: String)
    suspend fun createNewConversation(title: String): String
    suspend fun sendMessageWithFallback(model: String, conversationId: String?)
}
