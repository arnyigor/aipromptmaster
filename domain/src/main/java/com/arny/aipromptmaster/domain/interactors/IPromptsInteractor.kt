package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Pageable
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import kotlinx.coroutines.flow.Flow

interface IPromptsInteractor {
    // Основные операции с промптами
    fun getPrompts(): Flow<List<Prompt>>
    suspend fun getPromptById(id: String): Prompt?
    suspend fun savePrompt(prompt: Prompt): Long
    suspend fun updatePrompt(prompt: Prompt)
    suspend fun deletePrompt(promptId: String)
    
    // Поиск и фильтрация
    fun searchPrompts(
        query: String = "",
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList()
    ): Pageable<Prompt>
    
    // Синхронизация
    suspend fun synchronize(): IPromptSynchronizer.SyncResult
    suspend fun getLastSyncTime(): Long
}