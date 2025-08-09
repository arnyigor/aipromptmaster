package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ChatCompletionRequestDTO(
    val model: String,
    val messages: List<MessageDTO>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null
)

@Serializable
data class MessageDTO(
    val role: ChatMessageRole, // "user", "assistant", "system"
    val content: String
)

@Serializable
data class ChatCompletionResponseDTO(
    val id: String,
    val choices: List<ChoiceDTO>,
    val usage: UsageDTO?
)

@Serializable
data class ChoiceDTO(
    val message: MessageDTO,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class UsageDTO(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens")  val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
