package com.arny.aipromptmaster.data.openrouter

import android.util.Log
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenRouterRepositoryImpl @Inject constructor(
    private val service: OpenRouterService,
    private val dispatcher: CoroutineDispatcher // Например, Dispatchers.IO
) : IOpenRouterRepository {

    // 1. Приватный, изменяемый StateFlow для хранения кэша. Потокобезопасный.
    private val _modelsCache = MutableStateFlow<List<LlmModel>>(emptyList())

    // 2. Используем Mutex для предотвращения одновременных запросов в сеть.
    private val refreshMutex = Mutex()

    /**
     * Предоставляет публичный, неизменяемый Flow для доступа к кэшу моделей.
     */
    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsCache.asStateFlow()

    /**
     * Обновляет кэш моделей из сети.
     * Эту функцию будет вызывать Interactor или ViewModel при необходимости (например, pull-to-refresh).
     */
    override suspend fun refreshModels(): Result<Unit> = withContext(dispatcher) {
        // Блокируем Mutex, чтобы только одна корутина могла выполнять этот код одновременно.
        refreshMutex.withLock {
            try {
                val response = service.getModels()
                if (response.isSuccessful && response.body() != null) {
                    // При успехе обновляем значение в StateFlow.
                    // Все подписчики на getModelsFlow() автоматически получат новый список.
                    _modelsCache.value = response.body()!!.models.map { it.toDomain() }
                    Result.success(Unit)
                } else {
                    // В случае ошибки сети возвращаем Failure. Кэш не трогаем.
                    // UI продолжит показывать старые данные, что лучше, чем ничего.
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                // В случае исключения (нет сети и т.д.) также возвращаем Failure.
                Result.failure(e)
            }
        }
    }

    override suspend fun getChatCompletion(
        model: String,
        messages: List<Message>,
        apiKey: String,
    ): Result<ChatCompletionResponse> = withContext(dispatcher) {
        val request = ChatCompletionRequestDTO(
            model = model,
            messages = messages.map { MessageDTO(it.role, it.content) },
            maxTokens = 4096
        )
        Log.d("ChatDebug", "6.5. Request:$request")
        val response = service.getChatCompletion(
            authorization = "Bearer $apiKey",
            referer = "aiprompts", // Эти заголовки лучше вынести в Interceptor
            title = "AI Chat App",
            request = request
        )
        Log.d("ChatDebug", "7. response:$response")
        when {
            response.isSuccessful && response.body() != null -> {
                val domainResponse = ChatMapper.toDomain(response.body()!!)
                Result.success(domainResponse)
            }

            else -> {
                val errorMessage =
                    response.errorBody()?.string() ?: "API Error: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        }
    }
}
