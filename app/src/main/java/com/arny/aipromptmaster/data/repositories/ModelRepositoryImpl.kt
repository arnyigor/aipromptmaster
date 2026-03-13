package com.arny.aipromptmaster.data.repositories

import android.util.Log
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toEntity
import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class ModelRepositoryImpl(
    private val api: OpenRouterService,
    private val dao: ModelDao,
    private val syncMetadata: SyncMetadata
) : ModelRepository {

    private val favoriteMutex = Mutex()

    override fun getSelectedModelFlow(): Flow<DataResult<LlmModel>> = dao.getSelectedModelFlow()
        .map { entity ->
            if (entity == null) {
                DataResult.Error(DomainError.Generic("No model selected"))
            } else {
                DataResult.Success(entity.toDomain())
            }
        }

    override suspend fun getSelectedModel(): LlmModel? = try {
           dao.getSelectedModel()?.toDomain()
       } catch (e: Exception) {
           null
       }

    override fun getModels(filter: ModelsFilter): Flow<List<LlmModel>> =
        dao.getModels(
            searchQuery = filter.query,
            onlyFree = filter.isFreeOnly,
            onlyFavorites = filter.isFavoritesOnly,
            sortBy = filter.sortType.name
        ).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshModels(): Result<Unit> {
        val lastSync = syncMetadata.getLastSync()
        val hasModels = dao.getModelsCount() > 0
        Timber.tag("ModelRepo")
            .d("Last sync: $lastSync, ago: ${lastSync / 1000}s, cached: ${lastSync < 24 * 60 * 60_000L}")
        if (hasModels && System.currentTimeMillis() - lastSync < 24 * 60 * 60_000L) {
            Timber.tag("ModelRepo").d("Using cache, skipping API call")
            return Result.success(Unit)
        }

        return try {
            val response = api.getModels()
            Timber.tag("ModelRepo")
                .d("API response: ${response.code()}, body: ${response.body()?.models?.size}")
            if (!response.isSuccessful) throw Exception("API ${response.code()}")
            val dtos = response.body()?.models ?: emptyList()
            val entities = dtos.map(ModelDTO::toEntity)
            dao.syncModels(entities)
            syncMetadata.setLastSync(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Возвращает новое состояние isFavorite после переключения
     */
    override suspend fun toggleFavorite(modelId: String): Boolean {
        return favoriteMutex.withLock {
            // Читаем текущее состояние
            val currentModel = dao.getModelById(modelId)
                ?: throw DomainError.Local(StringHolder.Text("Model $modelId not found"))

            val newState = !currentModel.isFavorite

            // Обновляем в БД
            val rowsAffected = dao.updateFavoriteStatus(modelId, newState)
            if (rowsAffected == 0) {
                throw DomainError.Local(StringHolder.Text("Failed to update model $modelId"))
            }

            newState
        }
    }

    override suspend fun selectModel(modelId: String) {
        dao.selectModel(modelId)
    }
}
