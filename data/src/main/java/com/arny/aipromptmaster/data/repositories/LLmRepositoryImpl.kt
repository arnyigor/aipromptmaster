package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.providers.BaseLlmProvider
import com.arny.aipromptmaster.domain.models.LLMResponse
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.repositories.ILLmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LLmRepositoryImpl @Inject constructor(
    private val providers: Map<String, BaseLlmProvider>
) : ILLmRepository {

    @Volatile
    private var cachedModels: List<LLMModel>? = null

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        provider: String
    ): Result<LLMResponse> = withContext(Dispatchers.IO) {
        providers[provider]?.sendRequest(
            LLMRequest(messages, model, temperature)
        ) ?: Result.failure(IllegalArgumentException("Provider $provider not found"))
    }

    override suspend fun sendStreamingMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        provider: String
    ): Flow<Result<LLMResponse>> = flow {
        withContext(Dispatchers.IO) {
            providers[provider]?.streamRequest(
                LLMRequest(messages, model, temperature)
            )?.collect { result ->
                emit(result)
            } ?: emit(Result.failure(IllegalArgumentException("Provider $provider not found")))
        }
    }

    override suspend fun getModels(fresh: Boolean): Result<List<LLMModel>> = withContext(Dispatchers.IO) {
        try {
            if (!fresh && cachedModels != null) {
                return@withContext Result.success(cachedModels!!)
            }

            // TODO: Реализовать получение моделей для всех провайдеров
            cachedModels?.let { Result.success(it) }
                ?: Result.failure(Exception("Models not available"))
        } catch (e: Exception) {
            cachedModels?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }

    private data class LLMRequest(
        val messages: List<ChatMessage>,
        val model: String,
        val temperature: Float
    )
}