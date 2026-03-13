package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class PromptsInteractorImpl(
    private val repository: IPromptsRepository,
    private val synchronizer: IPromptSynchronizer
) : IPromptsInteractor {

    override fun observeAllPrompts(): Flow<List<Prompt>> = repository.observeAllPrompts()

    override suspend fun addCategory(categoryName: String) {
        // Добавить категорию в кэширование
        val currentSortData = getPromptsSortData()
        val updatedCategories = currentSortData.categories.toMutableList()
        if (!updatedCategories.contains(categoryName)) {
            updatedCategories.add(categoryName)
            val updatedSortData = currentSortData.copy(
                categories = updatedCategories.toList()
            )
            repository.cacheSortData(updatedSortData)
        }
    }

    override suspend fun getPromptsSortData(): PromptsSortData =
        repository.getCacheSortData() ?: loadAndCacheSortData()

    private suspend fun loadAndCacheSortData(): PromptsSortData {
        return coroutineScope {
            val categoriesDeferred = async { repository.getUniqueCategories() }
            val tagsDeferred = async { repository.getUniqueTags() }
            val newSortData = PromptsSortData(
                categories = categoriesDeferred.await(),
                tags = tagsDeferred.await()
            )
            repository.cacheSortData(newSortData)
            newSortData
        }
    }

    override fun observePrompt(id: String): Flow<Prompt?> = repository.observePrompt(id)

    override suspend fun toggleFavorite(promptId: String): Boolean {
        val prompt = repository.getPromptById(promptId)
        var result = false
        if (prompt != null) {
            result = repository.updatePrompt(prompt.copy(isFavorite = !prompt.isFavorite))
        }
        return result
    }

    override suspend fun getPrompt(promptId: String): Prompt? = repository.getPromptById(promptId)

    override suspend fun updatePrompt(prompt: Prompt): Boolean = repository.updatePrompt(prompt)

    override suspend fun deletePrompt(promptId: String) = repository.deletePrompt(promptId)

    override suspend fun synchronize(): SyncResult = synchronizer.synchronize()

    override suspend fun getLastSyncTime(): Long = synchronizer.getLastSyncTime()

    override suspend fun getUniqueCategories(): List<String> = repository.getUniqueCategories()

    override suspend fun getUniqueTags(): List<String> = repository.getUniqueTags()
    
    override suspend fun existsByTitle(title: String, excludeId: String?): Boolean =
        repository.existsByTitle(title, excludeId)
    
    override suspend fun insertPrompt(prompt: Prompt): Boolean =
        repository.insertPrompt(prompt)
}