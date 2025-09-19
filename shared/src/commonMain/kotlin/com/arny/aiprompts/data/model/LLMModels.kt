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

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto>
)

@Serializable
data class OpenRouterModelDto(
    val id: String,
    val name: String,
    val description: String,
    val created: Long,
    @Contextual val context_length: Long?,
    @Contextual val pricing: PricingDto?,
    val modalities: ModalitiesDto?
) {
    fun toDomain(): LlmModel = LlmModel(
        id = id,
        name = name,
        description = description,
        created = created,
        contextLength = context_length,
        pricingPrompt = pricing?.prompt,
        pricingCompletion = pricing?.completion,
        pricingImage = pricing?.image,
        inputModalities = modalities?.input ?: emptyList(),
        outputModalities = modalities?.output ?: emptyList(),
        isSelected = false
    )
}

@Serializable
data class PricingDto(
    @Contextual val prompt: BigDecimal?,
    @Contextual val completion: BigDecimal?,
    @Contextual val image: BigDecimal?
)

@Serializable
data class ModalitiesDto(
    val input: List<String>?,
    val output: List<String>?
)
