package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Pageable
import com.arny.aipromptmaster.domain.models.Prompt
import kotlinx.coroutines.flow.Flow

interface IPromptsRepository {
    suspend fun getPromptById(promptId: String): Prompt?
    suspend fun insertPrompt(prompt: Prompt): Long
    suspend fun updatePrompt(prompt: Prompt)
    suspend fun deletePrompt(promptId: String)
    suspend fun savePrompts(prompts: List<Prompt>)
    fun getAllPrompts(): Flow<List<Prompt>>
    
    fun getPromptsPaginated(
        search: String = "",
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList()
    ): Pageable<Prompt>
}