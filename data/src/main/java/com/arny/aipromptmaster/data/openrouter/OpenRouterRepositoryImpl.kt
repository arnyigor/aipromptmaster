package com.arny.aipromptmaster.data.openrouter

import android.util.Log
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.mappers.ChatMapper
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toDomainError
import com.arny.aipromptmaster.data.models.ApiErrorResponse
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.ChatCompletionResponseDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.results.DataResult
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
//                    Log.i(this::class.java.simpleName, "refreshModels: _modelsCache.value = ${_modelsCache.value.size}")
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
        messages: List<ChatMessage>,
        apiKey: String,
    ): Result<ChatCompletionResponse> = withContext(dispatcher) {
        val request = ChatCompletionRequestDTO(
            model = model,
            messages = messages.map { MessageDTO(it.role.toString(), it.content) },
            maxTokens = 4096
        )
        Log.d("ChatDebug", "6.5. Request:$request")

        try {
            val response = service.getChatCompletion(
                authorization = "Bearer $apiKey",
                referer = "aiprompts",
                title = "AI Chat App",
                request = request
            )
            Log.d("ChatDebug", "7. response:$response")

            if (response.isSuccessful && response.body() != null) {
                val domainResponse = ChatMapper.toDomain(response.body()!!)
                Result.success(domainResponse)
            } else {
                // Обработка неуспешных HTTP-статусов (4xx, 5xx)
                val errorBody = response.errorBody()?.string()
                val domainError = if (!errorBody.isNullOrBlank()) {
                    try {
                        // Пытаемся распарсить тело ошибки в нашу модель
                        val gson = Gson()
                        val errorResponse = gson.fromJson(errorBody, ApiErrorResponse::class.java)
                        errorResponse.error.toDomainError()
                    } catch (e: Exception) {
                        // Если парсинг не удался, возвращаем ошибку с сырым телом
                        DomainError.Generic("Не удалось обработать ответ сервера (Код: ${response.code()}). Тело ответа: $errorBody")
                    }
                } else {
                    // Тело ошибки пустое, используем код и сообщение ответа
                    DomainError.Api(
                        code = response.code(),
                        userFriendlyMessage = "Ошибка сервера (Код: ${response.code()})",
                        detailedMessage = response.message().ifBlank { "Ответ не содержит дополнительной информации." }
                    )
                }
                Result.failure(domainError)
            }
        } catch (e: Exception) {
            // Обработка сетевых ошибок (например, SocketTimeoutException, UnknownHostException)
            // и других непредвиденных исключений
            Log.e("ChatDebug", "Network or unexpected error", e)
            val domainError = when (e) {
                is java.net.SocketTimeoutException -> DomainError.Network("Превышено время ожидания ответа от сервера.")
                is java.net.UnknownHostException -> DomainError.Network("Не удалось подключиться к серверу. Проверьте интернет-соединение.")
                else -> DomainError.Generic("Произошла непредвиденная ошибка: ${e.localizedMessage}")
            }
            Result.failure(domainError)
        }
    }

    override fun getChatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Flow<DataResult<String>> = flow {
        val request = ChatCompletionRequestDTO(
            model = model,
            messages = messages.map { MessageDTO(it.role.toString(), it.content) },
            maxTokens = 4096,
            stream = true // <--- Включаем стриминг
        )

        try {
            val response = service.getChatCompletionStream(
                authorization = "Bearer $apiKey",
                referer = "aiprompts",
                title = "AI Chat App",
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Используем reader для чтения потока построчно
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        // SSE-события начинаются с "data: "
                        if (line?.startsWith("data:") == true) {
                            val json = line.substring(5).trim()
                            if (json == "[DONE]") {
                                // Сервер сообщил, что поток завершен
                                break
                            }
                            try {
                                val gson = Gson()
                                val chunk = gson.fromJson(json, ChatCompletionResponseDTO::class.java)
                                val delta = chunk.choices.firstOrNull()?.delta

                                // Проверяем сначала content, потом reasoning.
                                val contentDelta = delta?.content?.takeIf { it.isNotEmpty() }
                                    ?: delta?.reasoning?.takeIf { it.isNotEmpty() }

                                if (contentDelta != null) {
                                    // Эмитим только новую часть текста
                                    emit(DataResult.Success(contentDelta))
                                }
                            } catch (e: Exception) {
                                Log.w("ChatStream", "Failed to parse stream chunk: $json", e)
                                // Можно проигнорировать или эмитить ошибку
                            }
                        }
                    }
                }
            } else {
                // Обработка неуспешных HTTP-статусов (4xx, 5xx)
                val errorBody = response.errorBody()?.string()
                // ... (твоя логика обработки ошибок остается такой же)
                val domainError = parseErrorBody(response.code(), errorBody, response.message())
                emit(DataResult.Error(domainError))
            }
        } catch (e: Exception) {
            // ... (твоя логика обработки сетевых ошибок остается такой же)
            val domainError = mapNetworkException(e)
            emit(DataResult.Error(domainError))
        }
    }

    // Вынесем логику парсинга ошибок в отдельные функции для чистоты
    private fun parseErrorBody(code: Int, body: String?, message: String): DomainError { /* ... */ return TODO(
        "Provide the return value"
    )
    }
    private fun mapNetworkException(e: Exception): DomainError { /* ... */ return TODO("Provide the return value")
    }

}
