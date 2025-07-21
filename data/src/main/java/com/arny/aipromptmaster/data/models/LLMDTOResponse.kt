package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequestDTO(
    val model: String,
    val messages: List<MessageDTO>,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null
)

data class MessageDTO(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatCompletionResponseDTO(
    val id: String,
    val choices: List<ChoiceDTO>,
    val usage: UsageDTO?,
)

data class ApiErrorResponse(
    @SerializedName("error")
    val error: ApiError,
    @SerializedName("user_id")
    val userId: String?
)

data class ApiError(
    @SerializedName("message")
    val message: String,

    @SerializedName("code")
    val code: Int,

    @SerializedName("metadata")
    val metadata: ErrorMetadata? // Делаем nullable на случай, если metadata может отсутствовать
)

data class ErrorMetadata(
    @SerializedName("raw")
    val raw: String?,

    @SerializedName("provider_name")
    val providerName: String?
)

data class ChoiceDTO(
    val message: MessageDTO,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class UsageDTO(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)
