package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.mappers.PromptMapper
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptCategory
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
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun getPromptsByLanguage(language: String): Flow<List<Prompt>> = promptDao
        .getPromptsByLanguage(language)
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun getPromptsByCategory(category: PromptCategory): Flow<List<Prompt>> = promptDao
        .getPromptsByCategory(category)
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun getPromptsByAiModel(aiModel: String): Flow<List<Prompt>> = promptDao
        .getPromptsByAiModel(aiModel)
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun searchPrompts(query: String): Flow<List<Prompt>> = promptDao
        .searchPrompts(query)
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun getPromptVersions(parentId: String): Flow<List<Prompt>> = promptDao
        .getPromptVersions(parentId)
        .map { entities -> entities.map(PromptMapper::toDomain) }

    override suspend fun savePrompt(prompt: Prompt) = withContext(dispatcher) {
        promptDao.insertPrompt(PromptMapper.toEntity(prompt))
    }

    suspend fun updatePrompt(prompt: Prompt) = withContext(dispatcher) {
        promptDao.updatePrompt(PromptMapper.toEntity(prompt))
    }

    suspend fun deletePrompt(prompt: Prompt) = withContext(dispatcher) {
        promptDao.deletePrompt(PromptMapper.toEntity(prompt))
    }

    suspend fun updateRating(promptId: String, rating: Float) = withContext(dispatcher) {
        promptDao.updateRating(promptId, rating)
    }
}