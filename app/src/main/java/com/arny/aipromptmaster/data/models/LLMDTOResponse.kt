package com.arny.aipromptmaster.data.models

import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatCompletionRequestDTO(
    val model: String,
    val messages: List<MessageDTO>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class MessageDTO(
    val role: String, // "user", "assistant", "system"
    val content: String? = null,
    // Для мультимодальных сообщений - используется вместо content
    val contentList: List<ContentItemDTO>? = null,
    val reasoning: String? = null
) {
    /**
     * Создает MessageDTO с контентом как строкой (для обычных сообщений)
     */
    companion object {
        fun fromText(role: String, content: String): MessageDTO = MessageDTO(
            role = role,
            content = content
        )
    }

    /**
     * Создает MessageDTO с мультимодальным контентом (для сообщений с изображениями)
     */
    fun withMultimodalContent(contentItems: List<ContentItemDTO>): MessageDTO = copy(
        content = null,
        contentList = contentItems
    )
}

/**
 * Элемент контента для мультимодальных сообщений (OpenAI Vision API format)
 */
@Serializable
data class ContentItemDTO(
    val type: String, // "text" или "image_url"
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrlDTO? = null
) {
    companion object {
        fun text(text: String): ContentItemDTO = ContentItemDTO(
            type = "text",
            text = text
        )

        fun image(url: String): ContentItemDTO = ContentItemDTO(
            type = "image_url",
            imageUrl = ImageUrlDTO(url = url)
        )
    }
}

/**
 * URL изображения (может быть data URL с base64)
 */
@Serializable
data class ImageUrlDTO(
    val url: String
)

@Serializable
data class ChatCompletionResponseDTO(
    val id: String,
    val choices: List<ChoiceDTO>,
    val usage: UsageDTO? = null,
)

@Serializable
data class StreamChunk(
    val model: String? = null,
    val choices: List<ChoiceDelta>
)

@Serializable
data class ChoiceDelta(
    @SerialName("delta") val delta: DeltaDTO? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: ApiError,
    @SerialName("user_id") val userId: String? = null
)

private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

@Serializable
data class ApiError(
    @SerialName("message") val message: String,
    @SerialName("code") val code: Int,
    @SerialName("metadata") val metadata: ErrorMetadata? = null // Делаем nullable на случай, если metadata может отсутствовать
) {
    /** Имя провайдера, если есть. */
    fun providerName(): String? =
        metadata?.providerName

    /** Внутренний объект ошибки от провайдера. */
    fun innerProviderError(json: Json = DEFAULT_JSON): ProviderError? =
        metadata?.raw?.let { rawStr ->
            try { json.decodeFromString<ErrorRaw>(rawStr).error } catch (_: Exception) { null }
        }
}

@Serializable
data class ErrorMetadata(
    @SerialName("raw")            val raw: String? = null,   // ← строка JSON‑объекта
    @SerialName("provider_name") val providerName: String? = null,
)

@Serializable
data class ErrorRaw(
    @SerialName("error") val error: ProviderError? = null,
)

/**
 * Ошибка, возвращаемая конкретным LLM‑провайдером.
 */
@Serializable
data class ProviderError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    // Параметры, которые могут отсутствовать в некоторых ответах.
    @SerialName("param") val param: String? = null,
    @SerialName("type") val type: String? = null
)

@Serializable
data class ChoiceDTO(
    // Делаем оба поля nullable со значением по умолчанию,
    // чтобы один и тот же DTO мог парсить и стрим, и обычный ответ.
    val message: MessageDTO? = null,
    val delta: DeltaDTO? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class DeltaDTO(
    val role: String? = null,    // 'role' приходит только в первом чанке
    val content: String? = null // 'content' может быть null или отсутствовать
)

@Serializable
data class UsageDTO(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

/**
 * Добавляет в `StringBuilder` все непустые поля ошибки провайдера.
 */
private fun ProviderError.formatInto(b: StringBuilder) {
    b.appendLine("  code   : $code")
    b.appendLine("  message: $message")
    param?.let { b.appendLine("  param  : $it") }
    type?.let { b.appendLine("  type   : $it") }
}

/**
 * Быстрый однострочный / многострочный отчёт об ошибке.
 *
 * Выводит:
 *   • Provider (если известен)
 *   • Outer‑code и message (обязательные)
 *   • param  и type – только если они присутствуют в внутренней ошибке
 */
fun ApiError.fullReport(json: Json = DEFAULT_JSON): String =
    buildString {
        // 1. Имя провайдера (если непустое)
        providerName()
            ?.takeIf { it.isNotBlank() }
            ?.let { appendLine("Provider: $it") }

        // 2. Основная ошибка OpenRouter
        appendLine("code   : $code")
        appendLine("message: $message")

        // 3. Внутренний ответ провайдера (если есть)
        innerProviderError(json)?.let { err ->
            err.param
                ?.takeIf { it.isNotBlank() }
                ?.let { param -> appendLine("param  : $param") }

            err.type
                ?.takeIf { it.isNotBlank() }
                ?.let   { type   -> appendLine("type   : $type") }
        }
    }

/**
 * Конвертируем `ApiError` в доменную ошибку.
 *
 * @return DomainError.Api с корректно сформированным stringHolder и подробным сообщением.
 */
fun ApiError.toDomainError(): DomainError {

    val report = fullReport()

    return when (code) {
        400 -> {
            DomainError.Api(
                code = code,
                stringHolder = StringHolder.Resource(R.string.bad_request),
                detailedMessage = report
            )
        }

        401 -> DomainError.Api(
            code,
            StringHolder.Resource(R.string.authorization_error),
            report
        )

        429 -> DomainError.Api(
            code = code,
            stringHolder = StringHolder.Resource(R.string.rate_limit_exceeded),
            detailedMessage = report
        )

        500 -> DomainError.Api(
            code,
            StringHolder.Resource(R.string.server_error),
            report
        )

        else -> DomainError.Api(
            code,
            StringHolder.Resource(R.string.error_unknown_api),  // ← ресурс
            report
        )
    }
}
