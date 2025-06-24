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
    val usage: UsageDTO?
)

data class ChoiceDTO(
    val message: MessageDTO,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class UsageDTO(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens")  val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)
