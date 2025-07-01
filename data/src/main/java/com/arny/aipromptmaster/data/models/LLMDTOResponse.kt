package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequestDTO(
    val model: String,
    val messages: List<MessageDTO>,
    val stream: Boolean = false,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    @SerializedName("top_p")
    val topP: Float? = null
)

data class MessageDTO(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatCompletionResponseDTO(
    val id: String? = null,
    val choices: List<ChoiceDTO>,
    val usage: UsageDTO? = null,
    val model: String? = null,
    val created: Long? = null
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
