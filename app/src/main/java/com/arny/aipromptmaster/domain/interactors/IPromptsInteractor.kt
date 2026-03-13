package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.SyncResult
import kotlinx.coroutines.flow.Flow

interface IPromptsInteractor {
    fun observeAllPrompts(): Flow<List<Prompt>>
    suspend fun getPromptsSortData(): PromptsSortData
    fun observePrompt(id: String): Flow<Prompt?>
    suspend fun updatePrompt(prompt: Prompt): Boolean
    suspend fun deletePrompt(promptId: String)
    suspend fun synchronize(): SyncResult
    suspend fun getLastSyncTime(): Long?
    suspend fun toggleFavorite(promptId: String): Boolean
    suspend fun addCategory(categoryName: String)
    suspend fun getUniqueCategories(): List<String>
    suspend fun getUniqueTags(): List<String>
    suspend fun getPrompt(promptId: String): Prompt?
    suspend fun existsByTitle(title: String, excludeId: String? = null): Boolean
    suspend fun insertPrompt(prompt: Prompt): Boolean
}