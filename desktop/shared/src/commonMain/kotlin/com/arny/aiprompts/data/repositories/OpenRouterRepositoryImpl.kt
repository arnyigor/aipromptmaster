package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ApiException
import com.arny.aiprompts.data.model.AttachmentType
import com.arny.aiprompts.data.model.ChatCompletionRequest
import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
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
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    private var modelsProvider: com.arny.promptcontract.ProviderProfile? = null

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

    override fun getModelsFlow(): Flow<List<LlmModel>> = kotlinx.coroutines.flow.combine(_modelsFlow, settingsRepository.observeProviders()) { models, config ->
        if (modelsProvider?.id == config.activeId && modelsProvider?.baseUrl == config.active.baseUrl) models else emptyList()
    }

    override suspend fun refreshModels(): Result<Unit> = try {
        val provider = settingsRepository.loadProviders().active
        val url = "${provider.baseUrl.trimEnd('/')}/models"
        Logger.d("OpenRouterRepo", "Refreshing models from: $url")
        
        val response: ModelsResponseDTO = httpClient.get(url) {
            val key = com.arny.aiprompts.platform.resolveProviderKey(provider)
            if (key.isNotBlank()) header("Authorization", "Bearer $key")
        }.body()
        if (settingsRepository.loadProviders().activeId == provider.id) {
            modelsProvider = provider
            _modelsFlow.value = response.models.map { dto -> dto.toDomain() }
        }
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
        apiKey: String?,
        temperature: Double,
        maxTokens: Int
    ): Result<ChatCompletionResponse> {
        return try {
            val provider = settingsRepository.loadProviders().active
            val keyToUse = apiKey ?: com.arny.aiprompts.platform.resolveProviderKey(provider)

            if (keyToUse.isNullOrBlank() && provider.requiresKey) {
                return Result.failure(ApiException.MissingApiKey())
            }

            val url = "${provider.baseUrl.trimEnd('/')}/chat/completions"
            Logger.d("OpenRouterRepo", "Sending chat completion request to: $url")

            val request = OpenAiChatRequest(model = model,
                messages = buildMultimodalApiMessages(messages), stream = false,
                temperature = temperature, maxTokens = maxTokens)
            val httpResponse = httpClient.post(url) {
                if (!keyToUse.isNullOrBlank()) header("Authorization", "Bearer $keyToUse")
                contentType(ContentType.Application.Json)
                setBody(com.arny.promptcontract.completionRequestBody(model, provider.baseUrl, json.encodeToJsonElement(request.messages).jsonArray, false, maxTokens, temperature))
            }
            if (!httpResponse.status.isSuccess()) return Result.failure(ApiException.HttpError(httpResponse.status.value, "Провайдер отклонил запрос"))
            val response: com.arny.aiprompts.data.model.MultimodalChatResponse = httpResponse.body()

            if (response.error != null) {
                Logger.e("OpenRouterRepo", "API returned an error")
                return Result.failure(
                    ApiException.HttpError(
                        (response.error.code as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                        "Провайдер сообщил об ошибке"
                    )
                )
            }

            require(response.choices.orEmpty().none { it.finishReason == "length" }) { "Ответ обрезан лимитом токенов" }
            require(response.choices.orEmpty().none { it.finishReason == "content_filter" }) { "Ответ остановлен фильтром провайдера" }
            val choices = response.choices.orEmpty().map { choice ->
                com.arny.aiprompts.data.model.Choice(ChatMessage(role = ChatMessageRole.MODEL,
                    content = choice.message?.content.orEmpty()), choice.finishReason)
            }
            require(choices.any { it.message.content.isNotBlank() }) { "Провайдер вернул пустой ответ" }
            Result.success(ChatCompletionResponse(response.id, choices, response.usage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "OpenRouterRepo", "Chat completion failed")
            Result.failure(e)
        }
    }

    override fun getStreamingChatCompletion(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String?,
        temperature: Double,
        maxTokens: Int,
        topP: Double
    ): Flow<Result<StreamingChatChunk>> = flow {
        val provider = settingsRepository.loadProviders().active
            val keyToUse = apiKey ?: com.arny.aiprompts.platform.resolveProviderKey(provider)

        if (keyToUse.isNullOrBlank() && provider.requiresKey) {
            emit(Result.failure(ApiException.MissingApiKey()))
            return@flow
        }

        try {
            val url = "${provider.baseUrl.trimEnd('/')}/chat/completions"
            Logger.d("OpenRouterRepo", "Starting streaming request to: $url for model: $model")
            
            val requestBody = OpenAiChatRequest(
                model = model, messages = buildMultimodalApiMessages(messages), stream = true,
                maxTokens = maxTokens, temperature = temperature, topP = topP,
            )

            val response = httpClient.preparePost(url) {
                if (!keyToUse.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $keyToUse")
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.CacheControl, "no-cache")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
                setBody(com.arny.promptcontract.completionRequestBody(model, provider.baseUrl, json.encodeToJsonElement(requestBody.messages).jsonArray, true, maxTokens, temperature, topP))
            }.execute()

            if (!response.status.isSuccess()) {
                Logger.e("OpenRouterRepositoryImpl", "API Error: ${response.status}")
                emit(Result.failure(ApiException.HttpError(response.status.value, "Провайдер отклонил запрос")))
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
                        if (e is CancellationException) throw e
                        throw IllegalStateException("Не удалось подготовить изображение. Проверьте формат и размер файла.", e)
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
        val frame = com.arny.promptcontract.SseFrameDecoder()
        var completed = false
        suspend fun consume(data: String) {
            if (data == SSE_DONE_MARKER) {
                completed = true
                emit(StreamingChatChunk("", isComplete = true))
                return
            }
            val root = json.parseToJsonElement(data).jsonObject
            check(root["error"] == null || root["error"] == kotlinx.serialization.json.JsonNull) { "Провайдер сообщил об ошибке потока" }
            val chunk = json.decodeFromString<StreamingChatResponse>(data)
            val choice = chunk.choices?.firstOrNull() ?: return
            val content = choice.delta?.content.orEmpty()
            if (choice.finishReason != null) completed = true
            if (content.isNotEmpty() || choice.finishReason != null)
                emit(StreamingChatChunk(content, choice.finishReason, choice.finishReason != null))
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = channel.readUTF8Line() ?: break
            frame.accept(line)?.let { consume(it) }
        }
        frame.finish()?.let { consume(it) }
        check(completed) { "Ответ прерван: поток закрылся без завершения" }
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
