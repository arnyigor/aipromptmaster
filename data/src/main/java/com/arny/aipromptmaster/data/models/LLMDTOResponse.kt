package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val reasoning: String? = null
)

@Serializable
data class ChatCompletionResponseDTO(
    val id: String,
    val choices: List<ChoiceDTO>,
    val usage: UsageDTO?,
)

@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: ApiError,
    @SerialName("user_id")
    val userId: String?
)

@Serializable
data class ApiError(
    @SerialName("message") val message: String,
    @SerialName("code")  val code: Int,
    @SerialName("metadata")  val metadata: ErrorMetadata? // Делаем nullable на случай, если metadata может отсутствовать
)

@Serializable
data class ErrorMetadata(
    @SerialName("raw") val raw: String? = null,
    @SerialName("provider_name") val providerName: String? = null
)

@Serializable
data class ChoiceDTO(
    val delta: MessageDTO?,
    val message: MessageDTO,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UsageDTO(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
