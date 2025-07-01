package com.arny.aipromptmaster.data.providers

import com.arny.aipromptmaster.data.api.HuggingFaceApi
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.domain.models.LLMResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class HuggingFaceProvider @Inject constructor(
    private val api: HuggingFaceApi,
    apiKey: String
) : BaseLlmProvider(api.httpClient, apiKey, "huggingface") {

    override suspend fun sendRequest(request: LLMRequest): Result<LLMResponse> {
        return try {
            val response = api.generateText(
                authorization = "Bearer $apiKey",
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
        api.streamText(
            authorization = "Bearer $apiKey",
            request = request.toDTO()
        ).collect { dto ->
            emit(Result.success(ChatMapper.toDomain(dto)))
        }
    }

    private fun LLMRequest.toDTO() = mapOf(
        "inputs" to messages.joinToString("\n") { it.content },
        "parameters" to mapOf(
            "temperature" to temperature,
            "model" to model
        )
    )
}