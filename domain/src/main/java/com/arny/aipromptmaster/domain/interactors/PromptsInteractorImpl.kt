package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class PromptsInteractorImpl @Inject constructor(
    private val repository: IPromptsRepository
) : IPromptsInteractor {

    override suspend fun getAllPrompts(): Flow<List<Prompt>> = repository.getAllPrompts()

    override suspend fun createPrompt(prompt: Prompt): Flow<Unit> {
        repository.savePrompt(prompt)
        return flowOf(Unit)
    }

}