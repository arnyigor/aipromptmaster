package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toEntity
import com.arny.aipromptmaster.domain.models.Prompt
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

    override suspend fun getAllPrompts(): Flow<List<Prompt>> = promptDao
        .getAllPrompts()
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

    override suspend fun savePrompts(prompts: List<Prompt>) = withContext(dispatcher) {
        prompts.forEach { prompt ->
            val entity = prompt.toEntity()
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