package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Pageable
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PromptsInteractorImpl @Inject constructor(
    private val repository: IPromptsRepository,
    private val synchronizer: IPromptSynchronizer
) : IPromptsInteractor {

    override fun getPrompts(): Flow<List<Prompt>> = repository.getAllPrompts()

    override suspend fun getPromptById(id: String): Prompt? = repository.getPromptById(id)

    override suspend fun savePrompt(prompt: Prompt): Long = repository.insertPrompt(prompt)

    override suspend fun updatePrompt(prompt: Prompt) = repository.updatePrompt(prompt)

    override suspend fun deletePrompt(promptId: String) = repository.deletePrompt(promptId)

    override fun searchPrompts(
        query: String,
        category: String?,
        status: String?,
        tags: List<String>
    ): Pageable<Prompt> = repository.getPromptsPaginated(
        search = query,
        category = category,
        status = status,
        tags = tags
    )

    override suspend fun synchronize(): IPromptSynchronizer.SyncResult = synchronizer.synchronize()

    override suspend fun getLastSyncTime(): Long = synchronizer.getLastSyncTime()
}