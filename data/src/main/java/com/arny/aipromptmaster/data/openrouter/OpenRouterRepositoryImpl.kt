package com.arny.aipromptmaster.data.openrouter

import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenRouterRepositoryImpl @Inject constructor(
    private val service: OpenRouterService,
) : IOpenRouterRepository {

    @Volatile
    private var cachedModels: List<LLMModel>? = null

    override fun getChatCompletion(
        model: String,
        messages: List<Message>,
        apiKey: String,
        maxTokens: Int?,
        stream: Boolean
    ): Flow<ChatCompletionResponse> = flow {
        withContext(Dispatchers.IO) {
            if (!stream) {
                try {
                    val request = ChatCompletionRequestDTO(
                        model = model,
                        messages = messages.map { MessageDTO(it.role, it.content) },
                        maxTokens = maxTokens,
                        stream = false
                    )

                    val response = service.getChatCompletion(
                        authorization = "Bearer $apiKey",
                        referer = "aiprompts",
                        title = "AI Chat App",
                        request = request
                    )

                    when {
                        response.isSuccessful && response.body() != null -> {
                            val domainResponse = ChatMapper.toDomain(response.body()!!)
                            emit(domainResponse)
                        }
                        else -> {
                            val errorMessage = response.errorBody()?.string()
                                ?: "API Error: ${response.code()}"
                            throw Exception(errorMessage)
                        }
                    }
                } catch (e: Exception) {
                    throw e
                }
            } else {
                try {
                    val request = ChatCompletionRequestDTO(
                        model = model,
                        messages = messages.map { MessageDTO(it.role, it.content) },
                        maxTokens = maxTokens,
                        stream = true
                    )

                    service.getStreamChatCompletion(
                        authorization = "Bearer $apiKey",
                        referer = "aiprompts",
                        title = "AI Chat App",
                        request = request
                    ).collect { chunk ->
                        val domainResponse = ChatMapper.toDomain(chunk)
                        emit(domainResponse)
                    }
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }.catch { e ->
        throw e
    }
        try {
            val request = ChatCompletionRequestDTO(
                model = model,
                messages = messages.map { MessageDTO(it.role, it.content) },
                maxTokens = maxTokens,
                stream = false
            )

            val response = service.getChatCompletion(
                authorization = "Bearer $apiKey",
                referer = "aiprompts",
                title = "AI Chat App",
                request = request
            )

            when {
                response.isSuccessful && response.body() != null -> {
                    val domainResponse = ChatMapper.toDomain(response.body()!!)
                    Result.success(domainResponse)
                }

                else -> {
                    val errorMessage = response.errorBody()?.string()
                        ?: "API Error: ${response.code()}"
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    override suspend fun getModels(fresh: Boolean): Result<List<LLMModel>> = withContext(Dispatchers.IO) {
        try {
            // Возвращаем кеш, если он актуален и не запрошены свежие данные
            if (!fresh && cachedModels != null) {
                return@withContext Result.success(cachedModels!!)
            }

            val response = service.getModels()
            if (response.isSuccessful && response.body() != null) {
                val models = response.body()!!.models.map { it.toDomain() }
                cachedModels = models
                Result.success(models)
            } else {
                // Возвращаем кеш, если есть
                cachedModels?.let { Result.success(it) }
                    ?: Result.failure(Exception("Failed to fetch models"))
            }
        } catch (e: Exception) {
            cachedModels?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }
}