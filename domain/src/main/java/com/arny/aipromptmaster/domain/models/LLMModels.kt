package com.arny.aipromptmaster.domain.models

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val maxTokens: Int? = null,
    val temperature: Double? = null
)

data class Message(
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

data class LLMModel(
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int,
)
