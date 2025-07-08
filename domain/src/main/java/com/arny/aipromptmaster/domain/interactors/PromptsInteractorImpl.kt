package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class PromptsInteractorImpl @Inject constructor(
    private val repository: IPromptsRepository,
    private val synchronizer: IPromptSynchronizer
) : IPromptsInteractor {
    override suspend fun getPrompts(
        query: String,
        category: String?,
        status: String?,
        tags: List<String>,
        offset: Int,
        limit: Int
    ): List<Prompt> = repository.getPrompts(
        search = query,
        category = category,
        status = status,
        tags = tags,
        offset = offset,
        limit = limit
    )

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

    override suspend fun getPromptById(id: String): Prompt? = repository.getPromptById(id)

    override suspend fun savePrompt(prompt: Prompt): Long = repository.insertPrompt(prompt)

    override suspend fun updatePrompt(prompt: Prompt) = repository.updatePrompt(prompt)

    override suspend fun deletePrompt(promptId: String) = repository.deletePrompt(promptId)

    override suspend fun synchronize(): SyncResult = synchronizer.synchronize()

    override suspend fun getLastSyncTime(): Long = synchronizer.getLastSyncTime()
}