package com.arny.aipromptmaster.domain.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Список файлов привязанных к сообщению (для отображения)
    val attachedFileIds: List<String> = emptyList(),
    // Метаданные streaming (для reasoning)
    val thinkingTime: Long? = null, // Время обработки в миллисекундах
    val isThinking: Boolean = false
)

/**
 * Легковесные метаданные файла для истории
 * НЕ СОДЕРЖАТ полный контент
 */
data class FileAttachmentMetadata(
    val fileId: String,              // ID для получения из репозитория
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val preview: String,             // Только превью (первые 500 символов)
    val uploadTimestamp: Long = System.currentTimeMillis()
)

/**
 * Полное файловое вложение в репозитории
 */
data class FileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val originalContent: String,
    val isEditable: Boolean = true
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM;

    /**
     * ✅ Конвертирует enum в API-формат (lowercase)
     */
    fun toApiRole(): String = when (this) {
        USER -> "user"
        ASSISTANT -> "assistant"
        SYSTEM -> "system"
    }

    companion object {
        /**
         * ✅ Парсит API-роль в enum (case-insensitive)
         */
        fun fromApiRole(role: String): ChatRole = when (role.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            "system" -> SYSTEM
            else -> throw IllegalArgumentException("Unknown role: $role")
        }
    }
}

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val message: ChatMessage,
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
    val created: Long,
    val contextLength: BigDecimal,
    val pricingPrompt: BigDecimal,
    val pricingCompletion: BigDecimal,
    val pricingImage: BigDecimal?,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val isSelected: Boolean,
    val isFavorite: Boolean = false
)

/**
 * Модели данных для API запросов с файлами
 */
data class ApiRequestPayload(
    val messages: List<ApiMessage>,
    val attachedFiles: List<FileAttachment> = emptyList()
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class ApiRequestWithFiles(
    val model: String,
    val messages: List<ApiMessage>,
    val files: List<FileReference>
)

data class FileReference(
    val id: String,
    val name: String,
    val content: String,
    val mimeType: String
)

sealed class StreamChunk {
    data class Content(val text: String) : StreamChunk()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : StreamChunk()
}