package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import kotlinx.coroutines.flow.Flow

interface IPromptsRepository {
    suspend fun getPromptById(promptId: String): Prompt?
    suspend fun insertPrompt(prompt: Prompt): Long
    suspend fun updatePrompt(prompt: Prompt)
    suspend fun deletePrompt(promptId: String)
    suspend fun savePrompts(prompts: List<Prompt>)
    suspend fun getAllPrompts(): Flow<List<Prompt>>
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
}