package com.arny.promptcontract

import kotlinx.serialization.json.*

fun completionRequestBody(modelId: String, baseUrl: String, messages: JsonArray,
    stream: Boolean, maxTokens: Int, temperature: Double? = null, topP: Double? = null): JsonObject {
    val model = modelId.trim()
    require(model.isNotBlank()) { "Выберите модель" }
    require(maxTokens in 1..131072) { "Проверьте лимит ответа" }
    val modern = baseUrl.trim().trimEnd('/').lowercase() in setOf("https://api.openai.com/v1", "https://openrouter.ai/api/v1")
    val family = model.substringAfterLast('/').lowercase()
    val reasoning = Regex("^(o[1-9]|gpt-5|gpt-6)(?:[-.].*)?$").matches(family)
    return buildJsonObject {
        put("model", model); put("messages", messages); put("stream", stream)
        put(if (modern) "max_completion_tokens" else "max_tokens", maxTokens)
        if (!reasoning) {
            temperature?.let { require(it in 0.0..2.0); put("temperature", it) }
            topP?.let { require(it in 0.0..1.0); put("top_p", it) }
        }
    }
}
