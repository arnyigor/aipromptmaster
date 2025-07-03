package com.arny.aipromptmaster.domain.models

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val message: Message,
    val finishReason: String? = null
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class LlmModel(
    val id: String,
    val name: String,
    val description: String,
    val isSelected: Boolean,
)
