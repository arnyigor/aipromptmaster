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
    /**
     * Проверяет доступность модели, отправляя тестовый запрос.
     * @return true если модель доступна, false если недоступна
     */
    suspend fun checkModelAvailability(modelId: String): Boolean
/**
     * Проверяет доступность всех free моделей.
     * @param onProgress callback для отслеживания прогресса (текущая модель, количество проверенных, всего)
     * @return Flow<Pair<modelId, isAvailable>>
     */
    suspend fun checkFreeModelsAvailability(
        onProgress: ((modelId: String, checked: Int, total: Int) -> Unit)? = null
    ): Flow<Pair<String, Boolean>>
}