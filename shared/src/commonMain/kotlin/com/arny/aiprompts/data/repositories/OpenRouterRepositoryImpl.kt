package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatCompletionRequest
import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.ModelsResponseDTO
import com.arny.aiprompts.data.model.StreamingChatResponse
import com.arny.aiprompts.data.model.toDomain
import com.arny.aiprompts.utils.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString

class OpenRouterRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: ISettingsRepository
) : IOpenRouterRepository {

    private val _modelsFlow = MutableStateFlow<List<LlmModel>>(emptyList())

    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsFlow.asStateFlow()

    override suspend fun refreshModels(): Result<Unit> = try {
        val response: ModelsResponseDTO =
            httpClient.get("https://openrouter.ai/api/v1/models").body()
        _modelsFlow.value = response.models.map { dto -> dto.toDomain() }
        Logger.d("OpenRouterRepo", "Models refreshed successfully: ${response.models.size} models")
        Result.success(Unit)
    } catch (e: CancellationException) {
        // Normal cancellation when component is destroyed, don't treat as error
        Logger.d("OpenRouterRepo", "Models refresh cancelled")
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e(e, "OpenRouterRepo", "Failed to refresh models")
        Result.failure(e)
    }

    override suspend fun getChatCompletion(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String?
    ): Result<ChatCompletionResponse> {
        return try {
            // Используем переданный ключ или получаем из настроек
            val keyToUse = apiKey ?: settingsRepository.getOpenRouterApiKey()

            if (keyToUse.isNullOrBlank()) {
                return Result.failure(ApiException.MissingApiKey())
            }

            val response: ChatCompletionResponse =
                httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                    header("Authorization", "Bearer $keyToUse")
                    contentType(ContentType.Application.Json)
                    setBody(ChatCompletionRequest(model = model, messages = messages))
                }.body()

            // Проверяем, пришла ли в ответе ошибка
            if (response.error != null) {
                Logger.e("OpenRouterRepo", "API Error: ${response.error.message}")
                return Result.failure(
                    ApiException.HttpError(
                        response.error.code ?: 0,
                        response.error.message
                    )
                )
            }

            Result.success(response)

        } catch (e: Exception) {
            Logger.e(e, "OpenRouterRepo", "Chat completion failed")
            Result.failure(e)
        }
    }

    override fun getStreamingChatCompletion(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String?
    ): Flow<Result<StreamingChatChunk>> = flow {
        val keyToUse = apiKey ?: settingsRepository.getOpenRouterApiKey()

        if (keyToUse.isNullOrBlank()) {
            emit(Result.failure(ApiException.MissingApiKey()))
            return@flow
        }

        try {
            Logger.d("OpenRouterRepo", "Начинаем стриминговый запрос для модели: $model")
            val response = httpClient.preparePost("https://openrouter.ai/api/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $keyToUse")
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.CacheControl, "no-cache")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000 // 60 секунд
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
                setBody(
                    ChatCompletionRequest(
                        model = model,
                        messages = messages,
                        stream = true,
                        maxTokens = 4096,
                        temperature = 0.7
                    )
                )
            }.execute()

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e("OpenRouterRepositoryImpl", "API Error: ${response.status} - $errorBody")
                emit(Result.failure(ApiException.HttpError(response.status.value, errorBody)))
                return@flow
            }

            parseServerSentEvents(response.bodyAsChannel())
                .collect { chunk ->
                    currentCoroutineContext().ensureActive() // Проверка отмены
                    emit(Result.success(chunk))
                }

        } catch (e: CancellationException) {
            Logger.d("OpenRouterRepositoryImpl", "Streaming cancelled by user")
            throw e // Пробрасываем CancellationException
        } catch (e: Exception) {
            Logger.e(e, "Streaming error")
            emit(Result.failure(e))
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(Result.failure(e))
    }

    private fun parseServerSentEvents(channel: ByteReadChannel): Flow<StreamingChatChunk> = flow {
        while (!channel.isClosedForRead) {
            currentCoroutineContext().ensureActive()
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank() || line.startsWith(":")) continue

            if (line.startsWith(SSE_DATA_PREFIX)) {
                val jsonData = line.substring(SSE_DATA_PREFIX.length).trim()
                if (jsonData == SSE_DONE_MARKER) {
                    Logger.d("OpenRouterRepo", "Stream completed with DONE marker")
                    break
                }
                try {
                    val chunk = json.decodeFromString<StreamingChatResponse>(jsonData)
                    val choice = chunk.choices?.firstOrNull()
                    val delta = choice?.delta

                    // !-! изменено: просто пропустим если нет контента и finish_reason не пришел
                    if ((delta?.content == null || delta.content.isEmpty())
                        && choice?.finishReason == null
                    ) {
                        continue // skip
                    }

                    emit(
                        StreamingChatChunk(
                            content = delta?.content.orEmpty(),
                            finishReason = choice.finishReason,
                            isComplete = choice.finishReason != null
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Logger.w("OpenRouterRepo", "Failed to parse SSE chunk: $jsonData")
                    // continue
                }
            }
        }
    }

    companion object {
        const val SSE_DATA_PREFIX = "data: "
        const val SSE_DONE_MARKER = "[DONE]"
    }
}

// Модель для стриминговых чанков
data class StreamingChatChunk(
    val content: String,
    val finishReason: String? = null,
    val isComplete: Boolean = false
)

// Кастомные исключения
sealed class ApiException : Exception() {
    class MissingApiKey : ApiException() {
        override val message: String =
            "OpenRouter API ключ не настроен. Пожалуйста, введите API ключ в настройках."
    }

    data class HttpError(val code: Int, val body: String) : ApiException() {
        override val message: String = "API Error ($code): $body"
    }

    data class ParseError(val raw: String) : ApiException() {
        override val message: String = "Failed to parse response: $raw"
    }
}