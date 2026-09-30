package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

/**
 * Модель сессии чата (Domain layer).
 * Содержит полную информацию о чате включая настройки и сообщения.
 *
 * @property id Уникальный идентификатор сессии
 * @property name Название сессии (отображается в списке)
 * @property systemPrompt System prompt для данной сессии (может быть null)
 * @property settings Настройки генерации для сессии
 * @property createdAt Время создания сессии (timestamp)
 * @property updatedAt Время последнего обновления (timestamp)
 * @property isArchived Флаг архивирования сессии
 * @property modelId ID выбранной модели для сессии (null = использовать глобальную)
 * @property messages Список сообщений в сессии (по умолчанию пустой)
 */
@Serializable
data class ChatSession(
    val id: String,
    val name: String,
    val systemPrompt: String?,
    val settings: ChatSettings,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean = false,
    val modelId: String? = null,
    val messages: List<ChatMessage> = emptyList()
)

/**
 * Настройки чата для генерации ответов.
 *
 * @property temperature Температура генерации (0.0 - 2.0), по умолчанию 0.7
 * @property maxTokens Максимальное количество токенов, по умолчанию 2048
 * @property topP Top-p sampling параметр, по умолчанию 0.9
 * @property contextWindow Размер окна контекста (количество сообщений), по умолчанию 10
 */
@Serializable
data class ChatSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val contextWindow: Int = 10
)

/**
 * Расширения для ChatSession.
 */
fun ChatSession.getLastMessagePreview(maxLength: Int = 50): String {
    val lastMessage = messages.lastOrNull()?.content ?: ""
    return if (lastMessage.length > maxLength) {
        lastMessage.take(maxLength) + "..."
    } else {
        lastMessage
    }
}

fun ChatSession.getTotalTokens(): Int {
    return messages.sumOf { it.tokenCount ?: 0 }
}

fun ChatSettings.toMap(): Map<String, Any> = mapOf(
    "temperature" to temperature,
    "maxTokens" to maxTokens,
    "topP" to topP,
    "contextWindow" to contextWindow
)
