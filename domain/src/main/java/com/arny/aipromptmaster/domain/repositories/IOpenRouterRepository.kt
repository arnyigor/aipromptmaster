package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.Message

interface IOpenRouterRepository {

    suspend fun getChatCompletion(
        model: String,
        messages: List<Message>,
        maxTokens: Int? = null
    ): Result<ChatCompletionResponse>

    suspend fun getModels(): Result<List<LLMModel>>
}