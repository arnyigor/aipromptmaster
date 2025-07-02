package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow

interface ILLMInteractor {
    suspend fun sendMessage(model: String, userMessage: String): Flow<DataResult<String>>
    suspend fun getModels(): Flow<DataResult<List<LLMModel>>>
    suspend fun getSelectedModel(): Flow<DataResult<LLMModel>>
    suspend fun setSelectedModelId(id: String)
}
