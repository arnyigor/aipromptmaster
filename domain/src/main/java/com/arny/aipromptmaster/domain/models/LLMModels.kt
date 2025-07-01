package com.arny.aipromptmaster.domain.models

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class LLMModel(
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int,
)

// Универсальные модели, совместимые с OpenRouter/OpenAI API
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val topP: Float? = null
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>,
    val usage: Usage?,
    val model: String?
)

data class Choice(
    val message: ChatMessage?, // Для обычных ответов
    val delta: ChatMessage?,    // Для потоковых ответов (stream)
    val finishReason: String?
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// Модели, специфичные для Hugging Face API
data class HuggingFaceRequest(
    val inputs: String,
    val parameters: HuggingFaceParameters? = null,
    val options: HuggingFaceOptions? = null
)

data class HuggingFaceParameters(
    val temp: Float? = null,
    val topP: Float? = null,
    val maxNewTokens: Int? = null,
    val returnFullText: Boolean = false
)

// Опции запроса
data class HuggingFaceOptions(
    val waitForModel: Boolean = true,
    val useCache: Boolean = true
)

data class HuggingFaceResponse(
    val generatedText: String? = null,
    val error: String? = null
)
