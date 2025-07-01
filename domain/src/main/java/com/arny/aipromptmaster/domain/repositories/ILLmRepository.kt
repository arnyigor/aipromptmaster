package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.ChatMessage

import com.arny.aipromptmaster.domain.models.LLMResponse
import kotlinx.coroutines.flow.Flow

interface ILLmRepository {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        provider: String = "default"
    ): Result<LLMResponse>
    
    suspend fun sendStreamingMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        provider: String = "default"
    ): Flow<Result<LLMResponse>>
    
    suspend fun getModels(fresh: Boolean = false): Result<List<LLMModel>>
}