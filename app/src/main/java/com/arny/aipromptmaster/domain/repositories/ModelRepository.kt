package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun getModels(filter: ModelsFilter): Flow<List<LlmModel>>
    suspend fun refreshModels(): Result<Unit>
    suspend fun toggleFavorite(modelId: String): Boolean
    suspend fun selectModel(modelId: String)
    fun getSelectedModelFlow(): Flow<DataResult<LlmModel>>
    suspend fun getSelectedModel(): LlmModel?
}