package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import kotlinx.coroutines.flow.Flow

interface IPromptsRepository {
    suspend fun getPromptById(promptId: String): Prompt?
    suspend fun existsByTitle(title: String, excludeId: String? = null): Boolean
    suspend fun insertPrompt(prompt: Prompt): Boolean
    suspend fun updatePrompt(prompt: Prompt): Boolean
    suspend fun deletePrompt(promptId: String)
    suspend fun savePrompts(prompts: List<Prompt>)
    fun observeAllPrompts(): Flow<List<Prompt>>
    suspend fun getPrompts(
        search: String,
        category: String?,
        status: String?,
        tags: List<String>,
        offset: Int,
        limit: Int
    ): List<Prompt>

    suspend fun deletePromptsByIds(promptIds: List<String>)
    suspend fun getUniqueCategories(): List<String>
    suspend fun getUniqueTags(): List<String>
    fun invalidateSortDataCache()
    suspend fun getCacheSortData(): PromptsSortData?
    fun cacheSortData(sortData: PromptsSortData)
    suspend fun syncPrompts(prompts: List<Prompt>, ids: List<String>)
    fun observePrompt(id: String): Flow<Prompt?>
}