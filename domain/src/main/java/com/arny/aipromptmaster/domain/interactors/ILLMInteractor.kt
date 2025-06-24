package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.results.LLMResult
import kotlinx.coroutines.flow.Flow

interface ILLMInteractor {
    suspend fun sendMessage(model: String, userMessage: String): Flow<LLMResult<String>>
}
