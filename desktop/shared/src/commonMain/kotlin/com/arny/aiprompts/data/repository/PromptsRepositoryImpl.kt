@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.data.repository

import com.arny.aiprompts.data.db.daos.PromptDao
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.mappers.toEntity
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

class PromptsRepositoryImpl(
    private val promptDao: PromptDao,
    private val dispatcher: CoroutineDispatcher
) : IPromptsRepository {

    override suspend fun getPromptsCount(): Int = withContext(dispatcher) {
        promptDao.getPromptsCount()
    }

    override fun getAllPrompts(): Flow<List<Prompt>> = promptDao
        .getAllPromptsFlow()
        .map { entities -> entities.map { it.toDomain() } }

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

    override suspend fun deleteAllPrompts() = withContext(dispatcher) {
        promptDao.deleteAllPrompts()
    }

    override suspend fun toggleFavoriteStatus(promptId: String) = withContext(dispatcher) {
        promptDao.toggleFavoriteStatus(promptId)
    }

    override suspend fun savePrompts(prompts: List<Prompt>) = withContext(dispatcher) {
        promptDao.syncCatalog(prompts.map { it.toEntity() }, emptyList())
    }

    override suspend fun syncCatalog(prompts: List<Prompt>, deletedIds: List<String>) = withContext(dispatcher) {
        promptDao.syncCatalog(prompts.map { it.toEntity() }, deletedIds)
    }
    override suspend fun getPrompts(
        search: String,
        category: String?,
        status: String?,
        tags: List<String>,
        offset: Int,
        limit: Int
    ): List<Prompt> = withContext(dispatcher) {
        val prompts = if (tags.isEmpty()) {
            promptDao.getPromptsWithoutTags(
                searchQuery = search,
                category = category,
                status = status,
                limit = limit,
                offset = offset
            )
        } else {
            promptDao.getPromptsWithoutTags(
                searchQuery = search, category = category, status = status,
                limit = Int.MAX_VALUE, offset = 0
            ).filter { entity ->
                val storedTags = entity.tags.split(",").map { it.trim() }.toSet()
                tags.all { it in storedTags }
            }.drop(offset).take(limit)
        }.map { it.toDomain() }

        prompts
    }

    override suspend fun getAllUniqueTags(): List<String> = withContext(dispatcher) {
        promptDao.getAllPrompts().flatMap { entity ->
            if (entity.tags.isNotBlank()) {
                entity.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }.distinct().sorted()
    }

    override suspend fun invalidateSortDataCache() = withContext(dispatcher) {
        // TODO: Implement cache invalidation if needed
    }
}
