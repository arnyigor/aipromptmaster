package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow

interface ILLMInteractor {
    fun sendMessage(model: String, userMessage: String): Flow<DataResult<String>>
    fun getModels(): Flow<DataResult<List<LlmModel>>>
    fun getSelectedModel(): Flow<DataResult<LlmModel>>
    suspend fun selectModel(id: String)
    suspend fun refreshModels(): Result<Unit>
    suspend fun toggleModelSelection(clickedModelId: String)
    fun getChatHistoryFlow(): Flow<List<Message>>
    suspend fun clearChat()
}
