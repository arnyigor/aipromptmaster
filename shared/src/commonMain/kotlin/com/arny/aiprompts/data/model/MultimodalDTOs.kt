package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTO для мультимодальных запросов к OpenAI-совместимым API.
 * Поддерживает текст и изображения в одном сообщении.
 */
@Serializable
 data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessageDTO>,
    val stream: Boolean = true,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("temperature") val temperature: Double? = null
)

/**
 * DTO для сообщения в OpenAI API.
 * Content может быть String (для обратной совместимости) или Array (для Vision).
 */
@Serializable
 data class OpenAiMessageDTO(
    val role: String,
    // Используем JsonElement, так как это может быть String или Array
    val content: JsonElement
)

/**
 * Часть контента типа "text".
 */
@Serializable
 data class ContentPartText(
    val type: String = "text",
    val text: String
)

/**
 * Часть контента типа "image_url".
 */
@Serializable
 data class ContentPartImage(
    val type: String = "image_url",
    @SerialName("image_url") val imageUrl: ImageUrlData
)

/**
 * Данные изображения для Vision API.
 */
@Serializable
 data class ImageUrlData(
    val url: String // "data:image/jpeg;base64,..." или обычный URL
)

/**
 * DTO для ответа от мультимодального API.
 */
@Serializable
 data class MultimodalChatResponse(
    val id: String? = null,
    val choices: List<MultimodalChoice>? = null,
    val usage: Usage? = null,
    val error: ApiError? = null
)

/**
 * Choice для мультимодального ответа.
 */
@Serializable
 data class MultimodalChoice(
    val message: MultimodalMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * Сообщение в мультимодальном ответе.
 */
@Serializable
 data class MultimodalMessage(
    val role: String? = null,
    val content: String? = null // В ответе всегда строка
)
