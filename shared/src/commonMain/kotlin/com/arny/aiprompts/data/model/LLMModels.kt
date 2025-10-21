package com.arny.aiprompts.data.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

@Serializable
sealed class MessageStatus {
    @Serializable
    object Sent : MessageStatus()

    @Serializable
    data class Sending(val progress: Float = 0f) : MessageStatus()

    @Serializable
    data class Failed(val error: String) : MessageStatus()

    @Serializable
    data class Streaming(val isComplete: Boolean = false) : MessageStatus()
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatMessageRole,
    val content: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val status: MessageStatus = MessageStatus.Sent,
    val editedAt: Long? = null,
    val previousVersions: List<String> = emptyList(),
    val attachments: List<MessageAttachment> = emptyList(),
    val modelId: String? = null,
    val tokenCount: Int? = null
) {
    fun isStreaming(): Boolean = status is MessageStatus.Streaming

    fun isFailed(): Boolean = status is MessageStatus.Failed

    fun isSent(): Boolean = status is MessageStatus.Sent
}

@Serializable
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val uri: String,
    val mimeType: String? = null,
    val size: Long? = null
)

@Serializable
enum class AttachmentType {
    IMAGE, DOCUMENT, CODE
}

enum class ChatMessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    MODEL,

    @SerialName("system")
    SYSTEM;

    override fun toString(): String = when (this) {
        USER -> "user"
        MODEL -> "assistant"
        SYSTEM -> "system"
    }
}

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null, // Ошибка может быть ApiError или null
)

@Serializable
data class ApiError(
    val message: String,
    val code: Int? = null // Код ошибки может быть строкой или числом, String безопаснее
)

@Serializable
data class Choice(
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
    val completionTokensDetails: CompletionTokensDetails? = null,
    val promptTokensDetails: PromptTokensDetails? = null
)

@Serializable
data class LlmModel(
    val id: String,
    val name: String,
    val description: String,
    val created: Long,
    @Contextual val contextLength: Long?,
    @Contextual val pricingPrompt: BigDecimal?,
    @Contextual val pricingCompletion: BigDecimal?,
    @Contextual val pricingImage: BigDecimal?,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val isSelected: Boolean,
)

@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("stream") val stream: Boolean = false
)

// Модели для стримингового ответа
@Serializable
data class StreamingChatResponse(
    val id: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val choices: List<StreamingChatChoice>? = null,
    val usage: StreamingUsage? = null
)

@Serializable
data class StreamingUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val completionTokensDetails: CompletionTokensDetails? = null,
    val promptTokensDetails: PromptTokensDetails? = null
)

@Serializable
data class StreamingChatDelta(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_details") val reasoningDetails: List<ReasoningDetail>? = null
)

@Serializable
data class StreamingChatChoice(
    val index: Int? = null,
    val delta: StreamingChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ReasoningDetail(
    val id: String? = null,
    val type: String? = null,
    val text: String? = null,
    val format: String? = null,
    val index: Int? = null,
)

@Serializable
data class CompletionTokensDetails(
    val reasoningTokens: Int? = null,             // Токены для рассуждений
    val audioTokens: Int? = null,                 // Аудио-токены
    val acceptedPredictionTokens: Int? = null,     // Принятые токены предсказания
    val rejectedPredictionTokens: Int? = null      // Отклоненные токены предсказания
)

@Serializable
data class PromptTokensDetails(
    val cachedTokens: Int? = null,               // Кэшированные токены запроса
    val audioTokens: Int? = null                  // Аудио-токены в запросе
)
