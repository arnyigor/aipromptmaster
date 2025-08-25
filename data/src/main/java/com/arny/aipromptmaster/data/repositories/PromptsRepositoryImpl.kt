package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toEntity
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PromptsRepositoryImpl @Inject constructor(
    private val promptDao: PromptDao,
    private val dispatcher: CoroutineDispatcher
) : IPromptsRepository {
    @Volatile
    private var sortDataCache: PromptsSortData? = null

    override fun invalidateSortDataCache() {
        sortDataCache = null
    }

    override fun cacheSortData(sortData: PromptsSortData) {
        sortDataCache = sortData
    }

    override suspend fun getCacheSortData(): PromptsSortData? = sortDataCache

    override suspend fun getAllPrompts(): Flow<List<Prompt>> = promptDao
        .getAllPromptsFlow()
        .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getUniqueCategories(): List<String> = promptDao.getCategories()

    override suspend fun getUniqueTags(): List<String> {
        return promptDao.getTags()
            .flatMap { tags ->
                tags.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }.distinct()
    }

    override suspend fun getPromptById(promptId: String): Prompt? = withContext(dispatcher) {
        promptDao.getById(promptId)?.toDomain()
    }

    override suspend fun insertPrompt(prompt: Prompt): Long = withContext(dispatcher) {
        val entity = prompt.toEntity()
        promptDao.insertPrompt(entity)
    }

    override suspend fun updatePrompt(prompt: Prompt) = withContext(dispatcher) {
        val entity = prompt.toEntity()
        promptDao.updatePrompt(entity)
    }

    override suspend fun deletePrompt(promptId: String) = withContext(dispatcher) {
        promptDao.delete(promptId)
    }

    override suspend fun deletePromptsByIds(promptIds: List<String>) = withContext(dispatcher) {
        promptDao.deletePromptsByIds(promptIds)
    }

    override suspend fun savePrompts(prompts: List<Prompt>) = withContext(dispatcher) {
        // 1. Получаем все локальные промпты из базы ОДНИМ запросом.
        val localPrompts = promptDao.getAllPrompts().associateBy { it.id }

        // 2. Создаем "слитый" список.
        val mergedPrompts = prompts.map { remotePrompt ->
            // Ищем соответствующий локальный промпт.
            val localPrompt = localPrompts[remotePrompt.id]

            // Если локальный промпт существует и он избранный,
            // то мы создаем копию удаленного промпта, но с флагом isFavorite = true.
            if (localPrompt != null && localPrompt.isFavorite) {
                remotePrompt.copy(isFavorite = true)
            } else {
                // Иначе просто берем промпт с сервера как есть.
                remotePrompt
            }
        }

        // 3. Сохраняем "слитый" список в базу.
        // OnConflictStrategy.REPLACE теперь работает правильно: он заменяет данные,
        // но флаг isFavorite мы уже сохранили.
        val entitiesToSave = mergedPrompts.map { it.toEntity() }
        entitiesToSave.forEach { entity ->
            promptDao.insertPrompt(entity)
        }
    }

    override suspend fun getPrompts(
        search: String,
        category: String?,
        status: String?,
        tags: List<String>,
        offset: Int,
        limit: Int
    ): List<Prompt> = withContext(dispatcher) {
        promptDao.getPrompts(
            search = search,
            category = category,
            status = status,
            tags = tags.joinToString(","),
            limit = limit,
            offset = offset
        ).map { it.toDomain() }
    }
}