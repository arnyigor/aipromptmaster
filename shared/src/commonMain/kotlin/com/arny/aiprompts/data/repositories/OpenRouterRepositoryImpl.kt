package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ApiException
import com.arny.aiprompts.data.model.AttachmentType
import com.arny.aiprompts.data.model.ChatCompletionRequest
import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.ModelsResponseDTO
import com.arny.aiprompts.data.model.OpenAiChatRequest
import com.arny.aiprompts.data.model.OpenAiMessageDTO
import com.arny.aiprompts.data.model.StreamingChatChunk
import com.arny.aiprompts.data.model.StreamingChatResponse
import com.arny.aiprompts.data.model.getImageAttachments
import com.arny.aiprompts.data.model.isMultimodal
import com.arny.aiprompts.data.model.toDomain
import com.arny.aiprompts.domain.files.FilePromptProcessor
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Реализация репозитория взаимодействия с API OpenRouter.
 *
 * ПОДДЕРЖКА МУЛЬТИМОДАЛЬНОСТИ:
 * - Отправка изображений в Vision API (Base64)
 * - Оптимизация контекста: изображения отправляются только для последних N сообщений
 * - Поддержка текстовых файлов (встраиваются в текст сообщения)
 *
 * ПОДДЕРЖКА КАСТОМНЫХ BASE URL:
 * - Позволяет использовать локальные модели (LMStudio, Ollama и др.)
 * - Base URL настраивается через ISettingsRepository
 *
 * @property httpClient Клиент HTTP‑запросов (Ktor).
 * @property json Сериализатор/десериализатор Kotlinx‑Serialization.
 * @property settingsRepository Репозиторий настроек.
 * @property filePromptProcessor Процессор для подготовки файлов к отправке.
 */
class OpenRouterRepositoryImpl(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: ISettingsRepository,
    private val filePromptProcessor: FilePromptProcessor
) : IOpenRouterRepository {

    /** Состояние списка моделей в виде `MutableStateFlow`. */
    private val _modelsFlow = MutableStateFlow<List<LlmModel>>(emptyList())

    /**
     * Определяет Base URL динамически на основе настроек.
     */
    private val baseUrl: String
        get() {
            val customUrl = settingsRepository.getBaseUrl()
            return if (!customUrl.isNullOrBlank()) {
                customUrl.removeSuffix("/")
            } else {
                "https://openrouter.ai/api/v1"
            }
        }

    private val chatCompletionsUrl: String
        get() = "$baseUrl/chat/completions"

    private val modelsUrl: String
        get() = "$baseUrl/models"

    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsFlow.asStateFlow()

    override suspend fun refreshModels(): Result<Unit> = try {
        val url = modelsUrl
        Logger.d("OpenRouterRepo", "Refreshing models from: $url")
        
        val response: ModelsResponseDTO = httpClient.get(url).body()
        _modelsFlow.value = response.models.map { dto -> dto.toDomain() }
        Logger.d("OpenRouterRepo", "Models refreshed successfully: ${response.models.size} models")
        Result.success(Unit)
    } catch (e: CancellationException) {
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
            val keyToUse = apiKey ?: settingsRepository.getOpenRouterApiKey()

            if (keyToUse.isNullOrBlank()) {
                return Result.failure(ApiException.MissingApiKey())
            }

            val url = chatCompletionsUrl
            Logger.d("OpenRouterRepo", "Sending chat completion request to: $url")

            // Проверяем, нужен ли мультимодальный формат
            val hasMultimodalContent = messages.any { it.isMultimodal() }
            
            val response: ChatCompletionResponse = if (hasMultimodalContent) {
                // Используем мультимодальный формат
                val apiMessages = buildMultimodalApiMessages(messages)
                val request = OpenAiChatRequest(
                    model = model,
                    messages = apiMessages,
                    stream = false
                )
                httpClient.post(url) {
                    header("Authorization", "Bearer $keyToUse")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
            } else {
                // Обычный текстовый формат
                httpClient.post(url) {
                    header("Authorization", "Bearer $keyToUse")
                    contentType(ContentType.Application.Json)
                    setBody(ChatCompletionRequest(model = model, messages = messages))
                }.body()
            }

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
            val url = chatCompletionsUrl
            Logger.d("OpenRouterRepo", "Starting streaming request to: $url for model: $model")
            
            // Проверяем, нужен ли мультимодальный формат
            val hasMultimodalContent = messages.any { it.isMultimodal() }
            
            val requestBody = if (hasMultimodalContent) {
                val apiMessages = buildMultimodalApiMessages(messages)
                OpenAiChatRequest(
                    model = model,
                    messages = apiMessages,
                    stream = true,
                    maxTokens = 4096,
                    temperature = 0.7
                )
            } else {
                ChatCompletionRequest(
                    model = model,
                    messages = messages,
                    stream = true,
                    maxTokens = 4096,
                    temperature = 0.7
                )
            }
            
            val response = httpClient.preparePost(url) {
                header(HttpHeaders.Authorization, "Bearer $keyToUse")
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.CacheControl, "no-cache")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
                setBody(requestBody)
            }.execute()

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Logger.e("OpenRouterRepositoryImpl", "API Error: ${response.status} - $errorBody")
                emit(Result.failure(ApiException.HttpError(response.status.value, errorBody)))
                return@flow
            }

            parseServerSentEvents(response.bodyAsChannel())
                .collect { chunk ->
                    currentCoroutineContext().ensureActive()
                    emit(Result.success(chunk))
                }

        } catch (e: CancellationException) {
            Logger.d("OpenRouterRepositoryImpl", "Streaming cancelled by user")
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Streaming error")
            emit(Result.failure(e))
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(Result.failure(e))
    }

    /**
     * Строит список сообщений для API с поддержкой мультимодальности.
     * 
     * ОПТИМИЗАЦИЯ: Изображения отправляются только для последних [RECENT_MESSAGES_WITH_IMAGES] сообщений.
     * Для более старых сообщений изображения заменяются на текстовые заглушки.
     * Это экономит токены и предотвращает ошибки context_length_exceeded.
     */
    private suspend fun buildMultimodalApiMessages(
        messages: List<ChatMessage>
    ): List<OpenAiMessageDTO> {
        val apiMessages = mutableListOf<OpenAiMessageDTO>()
        
        // Индекс, с которого начинаем отправлять изображения (последние N сообщений)
        val thresholdIndex = (messages.size - RECENT_MESSAGES_WITH_IMAGES).coerceAtLeast(0)
        
        messages.forEachIndexed { index, msg ->
            val isRecent = index >= thresholdIndex
            val contentElement = buildMessageContent(msg, isRecent)
            
            apiMessages.add(OpenAiMessageDTO(
                role = msg.role.toString().lowercase(),
                content = contentElement
            ))
        }
        
        return apiMessages
    }

    /**
     * Строит контент для одного сообщения.
     * 
     * @param msg Сообщение
     * @param includeImages Если true, изображения будут включены как Base64
     * @return JsonElement для поля content (String или Array)
     */
    private suspend fun buildMessageContent(
        msg: ChatMessage,
        includeImages: Boolean
    ): JsonElement {
        // Если нет вложений или это текстовое сообщение без картинок - возвращаем просто строку
        if (msg.attachments.isEmpty() || !msg.isMultimodal()) {
            return JsonPrimitive(msg.content)
        }
        
        // Есть вложения - строим массив content parts
        return buildJsonArray {
            // Текстовая часть (включает текст сообщения + текстовые файлы)
            addJsonObject {
                put("type", "text")
                put("text", msg.content)
            }
            
            // Изображения
            val imageAttachments = msg.getImageAttachments()
            imageAttachments.forEach { attachment ->
                if (includeImages) {
                    // Для свежих сообщений - отправляем полное изображение Base64
                    try {
                        val base64 = filePromptProcessor.platformFileHandler.readImageToBase64(attachment.uri)
                        val mimeType = attachment.mimeType ?: "image/jpeg"
                        
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:$mimeType;base64,$base64")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e, "OpenRouterRepo", "Failed to read image: ${attachment.uri}")
                        // Заглушка при ошибке
                        addJsonObject {
                            put("type", "text")
                            put("text", "[Image: ${attachment.uri.substringAfterLast("/")}]")
                        }
                    }
                } else {
                    // Для старых сообщений - только заглушка
                    addJsonObject {
                        put("type", "text")
                        put("text", "[Previous image]")
                    }
                }
            }
        }
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
                }
            }
        }
    }

    companion object {
        const val SSE_DATA_PREFIX = "data: "
        const val SSE_DONE_MARKER = "[DONE]"
        
        /**
         * Количество последних сообщений, для которых отправляются изображения.
         * Более старые изображения заменяются на текстовые заглушки для экономии токенов.
         */
        const val RECENT_MESSAGES_WITH_IMAGES = 3
    }
}
