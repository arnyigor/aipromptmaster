package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptCategory
import kotlinx.coroutines.flow.Flow

interface IPromptsRepository {
    suspend fun getAllPrompts(): Flow<List<Prompt>>
    suspend fun getPromptsByLanguage(language: String): Flow<List<Prompt>>
    suspend fun getPromptsByCategory(category: PromptCategory): Flow<List<Prompt>>
    suspend fun getPromptsByAiModel(aiModel: String): Flow<List<Prompt>>
    suspend fun searchPrompts(query: String): Flow<List<Prompt>>
    suspend fun getPromptVersions(parentId: String): Flow<List<Prompt>>
    suspend fun savePrompt(prompt: Prompt)
}