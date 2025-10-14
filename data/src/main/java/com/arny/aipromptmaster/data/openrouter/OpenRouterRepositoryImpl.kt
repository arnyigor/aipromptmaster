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
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.ApiRequestWithFiles
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class OpenRouterRepositoryImpl @Inject constructor(
    private val service: OpenRouterService,
    private val jsonParser: Json, // Инжектим наш настроенный Json парсер
    private val dispatcher: CoroutineDispatcher
) : IOpenRouterRepository {

    private val _modelsCache = MutableStateFlow<List<LlmModel>>(emptyList())
    private val refreshMutex = Mutex()

    @Volatile
    private var isRequestCancelled = false

    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsCache.asStateFlow()

    override suspend fun refreshModels(): Result<Unit> = withContext(dispatcher) {
        refreshMutex.withLock {
            try {
                // Теперь Retrofit сам парсит ответ благодаря ConverterFactory
                val response = service.getModels()
                if (response.isSuccessful && response.body() != null) {
                    _modelsCache.value = response.body()!!.models.map { it.toDomain() }
                    Result.success(Unit)
                } else {
                    val error = parseErrorBody(
                        response.code(),
                        response.errorBody()?.string(),
                        response.message()
                    )
                    Result.failure(error)
                }
            } catch (e: Exception) {
                Result.failure(mapNetworkException(e))
            }
        }
    }

    // Хелпер для парсинга тела ошибки
    private fun parseErrorBody(code: Int, body: String?, message: String): DomainError {
        if (body.isNullOrBlank()) {
            return DomainError.Api(code, "Ошибка сервера (Код: $code)", message)
        }

        return try {
            val errorResponse = jsonParser.decodeFromString<ApiErrorResponse>(body)
            errorResponse.error.toDomainError()
        } catch (e: Exception) {
            Log.e("ParseError", "Failed to parse error response: $body", e)
            // Fallback для неизвестного формата ошибки
            DomainError.Api(
                code = code,
                stringHolder = StringHolder.Text("Ошибка сервера (Код: $code)"),
                detailedMessage = body
            )
        }
    }

    // Хелпер для маппинга сетевых исключений
    private fun mapNetworkException(e: Throwable): DomainError {
        Log.e("RepositoryError", "Network or unexpected error", e)
        return when (e) {
            is java.net.SocketTimeoutException -> DomainError.network(R.string.error_timeout)
            is java.net.UnknownHostException -> DomainError.network(R.string.error_no_internet)
            is java.net.ConnectException -> DomainError.network(R.string.error_connection)
            else -> DomainError.Generic(e.localizedMessage ?: "Неизвестная ошибка")
        }
    }

    override fun cancelCurrentRequest() {
        isRequestCancelled = true
        Log.d("OpenRouterRepository", "Запрос отменен")
    }

    override fun getChatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Flow<DataResult<String>> = flow {
        val request = ChatCompletionRequestDTO(
            model = model,
            messages = messages
                .map { MessageDTO(it.role.toApiRole(), it.content) }
                .filter { !it.content.isNullOrBlank() },
            maxTokens = 4096,
            stream = true
        )

        try {
            val response = service.getChatCompletionStream("Bearer $apiKey", request = request)

            if (response.isSuccessful && response.body() != null) {
                response.body()!!.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line?.startsWith("data:") == true) {
                            val json = line.substring(5).trim()
                            if (json == "[DONE]") break
                            try {
                                val chunk = jsonParser.decodeFromString<ChatCompletionResponseDTO>(json)
                                val contentDelta = chunk.choices.firstOrNull()?.delta?.content
                                if (contentDelta != null) {
                                    emit(DataResult.Success(contentDelta))
                                }
                            } catch (e: Exception) {
                                Log.w("ChatStream", "Failed to parse stream chunk: $json", e)
                            }
                        }
                    }
                }
            } else {
                emit(
                    DataResult.Error(
                        parseErrorBody(
                            response.code(),
                            response.errorBody()?.string(),
                            response.message()
                        )
                    )
                )
            }
        } catch (exception: Exception) {
            val domainError = mapNetworkException(exception)
            emit(DataResult.Error(domainError))
        }
    }

    /**
     * Файлы обрабатываются в LLMInteractor.buildMessagesForApi()
     */
    override fun getChatCompletionStreamWithFiles(
        request: ApiRequestWithFiles,
        apiKey: String
    ): Flow<DataResult<String>> = flow {
        emit(DataResult.Loading)

        try {
            val apiRequest = ChatCompletionRequestDTO(
                model = request.model,
                messages = request.messages.map { apiMsg ->
                    MessageDTO(
                        role = apiMsg.role,
                        content = apiMsg.content
                    )
                },
                stream = true
            )

            val response = service.getChatCompletionStream("Bearer $apiKey", request = apiRequest)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                emit(DataResult.Error(parseErrorBody(response.code(), errorBody, response.message())))
                return@flow
            }

            response.body()?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()
                    if (line?.startsWith("data: ") == true) {
                        val json = line.substring(6).trim()
                        if (json != "[DONE]") {
                            try {
                                val streamResponse = jsonParser.decodeFromString<ChatCompletionResponseDTO>(json)
                                val content = streamResponse.choices.firstOrNull()?.delta?.content
                                if (content != null) {
                                    emit(DataResult.Success(content))
                                }
                            } catch (e: Exception) {
                                Log.e("OpenRouterRepo", "Error parsing chunk: $json", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(DataResult.Error(mapNetworkException(e)))
        }
    }

}
