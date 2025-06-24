package com.arny.aipromptmaster.data.openrouter

import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.prefs.PrefsConstants
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenRouterRepositoryImpl @Inject constructor(
    private val service: OpenRouterService,
    private val prefs: Prefs,
) : IOpenRouterRepository {

    override suspend fun getChatCompletion(
        model: String,
        messages: List<Message>,
        maxTokens: Int?
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequestDTO(
                model = model,
                messages = messages.map { MessageDTO(it.role, it.content) },
                maxTokens = maxTokens
            )
            val apiKey = prefs.get<String>(PrefsConstants.OR_API_KEY)

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


    override suspend fun getModels(): Result<List<LLMModel>> = withContext(Dispatchers.IO) {
        try {
            val response = service.getModels()
            when {
                response.isSuccessful && response.body() != null -> {
                    val models = response.body()?.models.orEmpty()
                        .map { it.toDomain() }
                    Result.success(models)
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
}