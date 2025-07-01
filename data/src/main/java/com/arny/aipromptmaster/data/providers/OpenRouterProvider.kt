package com.arny.aipromptmaster.data.providers

import com.arny.aipromptmaster.data.api.OpenRouterApi
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.data.models.LLMResponseDTO
import com.arny.aipromptmaster.domain.models.LLMResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class OpenRouterProvider @Inject constructor(
    private val api: OpenRouterApi,
    apiKey: String
) : BaseLlmProvider(api.httpClient, apiKey, "openrouter") {

    override suspend fun sendRequest(request: LLMRequest): Result<LLMResponse> {
        return try {
            val response = api.getChatCompletion(
                authorization = "Bearer $apiKey",
                referer = "aiprompts",
                title = "AI Chat App",
                request = request.toDTO()
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(ChatMapper.toDomain(response.body()!!))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "API Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamRequest(request: LLMRequest): Flow<Result<LLMResponse>> = flow {
        api.getStreamChatCompletion(
            authorization = "Bearer $apiKey",
            referer = "aiprompts",
            title = "AI Chat App",
            request = request.toDTO().copy(stream = true)
        ).collect { dto ->
            emit(Result.success(ChatMapper.toDomain(dto)))
        }
    }

    private fun LLMRequest.toDTO() = LLMResponseDTO(
        model = model,
        messages = messages.map { it.toDTO() },
        temperature = temperature
    )
}