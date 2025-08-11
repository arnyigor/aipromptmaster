package com.arny.aiprompts.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.*

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatMessageRole, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long
)

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
    @SerialName("total_tokens") val totalTokens: Int
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
