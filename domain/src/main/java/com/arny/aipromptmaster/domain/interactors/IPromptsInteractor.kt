package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import kotlinx.coroutines.flow.Flow

interface IPromptsInteractor {
    suspend fun getAllPrompts(): Flow<List<Prompt>>
    suspend fun createPrompt(prompt: Prompt): Flow<Unit>
}