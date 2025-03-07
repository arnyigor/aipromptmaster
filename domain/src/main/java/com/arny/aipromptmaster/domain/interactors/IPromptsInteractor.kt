package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt

interface IPromptsInteractor {
    suspend fun getAllPrompts(): Result<List<Prompt>>
    suspend fun createPrompt(prompt: Prompt): Result<Unit>
    suspend fun getSuggestedTags(query: String): Result<List<String>>
}