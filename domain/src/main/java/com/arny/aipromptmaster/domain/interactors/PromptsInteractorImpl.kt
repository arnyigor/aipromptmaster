package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import javax.inject.Inject

class PromptsInteractorImpl @Inject constructor(
    private val repository: IPromptsRepository
) : IPromptsInteractor {

    override suspend fun getAllPrompts(): Result<List<Prompt>> {
        return repository.getAllPrompts()
    }

    override suspend fun createPrompt(prompt: Prompt): Result<Unit> {
        repository.savePrompt(prompt)
    }

    override suspend fun getSuggestedTags(query: String): Result<List<String>> {
        return if (query.isEmpty()) {
            repository.getPopularTags()
        } else {
            repository.searchTags(query)
        }
    }
}