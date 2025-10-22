package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ApiException
import com.arny.aiprompts.data.model.ChatCompletionRequest
import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.ModelsResponseDTO
import com.arny.aiprompts.data.model.StreamingChatChunk
import com.arny.aiprompts.data.model.StreamingChatResponse
import com.arny.aiprompts.data.model.toDomain
import com.arny.aiprompts.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Реализация репозитория взаимодействия с API OpenRouter.
 *
 * Вся логика, связанная с сетевыми запросами и парсингом SSE‑потока,
 * находится в этом классе. Он использует `HttpClient` из Ktor и
 * `Json` для сериализации/десериализации JSON‑объектов.
 *
 * @property httpClient Клиент HTTP‑запросов (Ktor).
 * @property json Сериализатор/десериализатор Kotlinx‑Serialization.
 * @property settingsRepository Репозиторий настроек, из которого берётся API‑ключ
 * при необходимости.
 */
class OpenRouterRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: ISettingsRepository
) : IOpenRouterRepository {

    /** Состояние списка моделей в виде `MutableStateFlow`. */
    private val _modelsFlow = MutableStateFlow<List<LlmModel>>(emptyList())

    /**
     * Возвращает поток с текущим списком доступных моделей.
     *
     * Публикует список при каждом обновлении `_modelsFlow`.
     *
     * @return Поток со списком `LlmModel`.
     */
    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsFlow.asStateFlow()

    /**
     * Запрашивает у OpenRouter актуальный список моделей и сохраняет его в
     * `_modelsFlow`. Если запрос завершился ошибкой, результат будет содержать
     * исключение.
     *
     * При отмене операции (`CancellationException`) метод просто завершается без
     * генерации ошибки – это нормальное поведение при разрушении UI‑компонента.
     *
     * @return `Result<Unit>` – `Success` при успешном обновлении,
     * иначе `Failure` с подробным исключением.
     */
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

    /**
     * Делает запрос к OpenRouter за завершённым чатом.
     *
     * Если в ответе присутствует поле `error`, генерируется `ApiException.HttpError`.
     * Если ключ API не найден – генерируется `ApiException.MissingApiKey`.
     *
     * @param model Идентификатор модели, которую нужно вызвать.
     * @param messages Список сообщений чата (история).
     * @param apiKey Ключ API. Если `null`, берётся из настроек через
     * `settingsRepository.getOpenRouterApiKey()`.
     * @return `Result<ChatCompletionResponse>` – успешный результат с объектом
     * ответа или исключением.
     */
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

    /**
     * Выполняет потоковый запрос к OpenRouter. Возвращает `Flow<Result<StreamingChatChunk>>`,
     * где каждый элемент представляет собой часть ответа модели.
     *
     * Если в процессе запроса возникнет `CancellationException`, он будет проброшен
     * наружу, что позволит отменить поток из UI‑компонента.
     *
     * @param model Идентификатор модели.
     * @param messages История чата.
     * @param apiKey Ключ API. Если `null`, берётся из настроек.
     * @return Поток с результатами – либо успешные чанки, либо ошибки.
     */
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

    /**
     * Парсит серверные события (SSE), полученные в `ByteReadChannel`.
     *
     * Внутри цикла читается строка, отбрасываются пустые строки и комментарии.
     * Если строка начинается с префикса `data:`, из неё извлекаются JSON‑данные,
     * десериализуются в `StreamingChatResponse` и формируются `StreamingChatChunk`.
     *
     * @param channel Канал, содержащий байты SSE‑потока.
     * @return Поток с частями ответа модели (`StreamingChatChunk`).
     */
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

                    // Пропускаем пустые куски без finish_reason
                    if ((delta?.content == null || delta.content.isEmpty())
                        && choice?.finishReason == null
                    ) {
                        continue
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
                    // Продолжаем обработку следующего сообщения
                }
            }
        }
    }

    private companion object {
        const val SSE_DATA_PREFIX = "data: "
        const val SSE_DONE_MARKER = "[DONE]"
    }
}
