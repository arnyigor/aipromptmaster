package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.domain.model.Prompt
import kotlinx.coroutines.flow.Flow

interface IPromptsRepository {
    suspend fun getPromptsCount(): Int
    suspend fun getPromptById(promptId: String): Prompt?
    suspend fun insertPrompt(prompt: Prompt): Long
    suspend fun updatePrompt(prompt: Prompt)
    suspend fun deletePrompt(promptId: String)
    suspend fun savePrompts(prompts: List<Prompt>)
    fun getAllPrompts(): Flow<List<Prompt>>
    suspend fun toggleFavoriteStatus(promptId: String)
    suspend fun getPrompts(
        search: String = "",
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 20
    ): List<Prompt>

    suspend fun deletePromptsByIds(promptIds: List<String>)
    suspend fun deleteAllPrompts()
    suspend fun getAllUniqueTags(): List<String>
    suspend fun invalidateSortDataCache()
}