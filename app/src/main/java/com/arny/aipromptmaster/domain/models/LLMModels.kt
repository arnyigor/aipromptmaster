package com.arny.aipromptmaster.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

/**
 * Сообщение в чате.
 * @Stable гарантирует, что Compose будет считать объект стабильным
 * при сравнении, что предотвращает лишние рекомпозиции.
 */
@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole, // "user", "assistant", "system"
    val content: String,
    val modelId: String? = null, // ID модели, которая сгенерировала ответ (только для AI)
    val state: MessageState = MessageState.PENDING,
    val fileAttachment: FileAttachmentMetadata? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageState{
    DONE,
    SENDING,
    PENDING
}

/**
 * Легковесные метаданные файла для истории
 * НЕ СОДЕРЖАТ полный контент
 */
@Serializable
@Immutable
data class FileAttachmentMetadata(
    val fileId: String,              // ID для получения из репозитория
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val preview: String,             // Только превью (первые 500 символов)
    val uploadTimestamp: Long = System.currentTimeMillis()
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isText: Boolean get() = mimeType.startsWith("text/") || 
                                 fileExtension.lowercase() in listOf("txt", "md", "json", "xml", "csv", "py", "java", "kt", "js", "html", "css")
}

/**
 * Полное файловое вложение в репозитории
 */
data class FileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val originalContent: String,      // Для текстовых файлов
    val imageData: ByteArray? = null, // Для изображений (raw bytes)
    val isEditable: Boolean = true
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isText: Boolean get() = mimeType.startsWith("text/") || 
                                 fileExtension.lowercase() in listOf("txt", "md", "json", "xml", "csv", "py", "java", "kt", "js", "html", "css")

    fun toMetadata() = FileAttachmentMetadata(
        fileId = this.id,
        fileName = this.fileName,
        fileExtension = this.fileExtension,
        fileSize = this.fileSize,
        mimeType = this.mimeType,
        preview = if (isText) this.originalContent.take(100) else if (isImage) "[Изображение]" else "[Файл]"
    )
}

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
    val contextLength: String,
    val pricingPrompt: String,
    val pricingCompletion: String,
    val pricingImage: String?,
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

/**
 * Результат стриминга - содержит контент чанка и опционально модель из ответа.
 * Модель может отличаться от запрошенной (роутинг/фоллбек в OpenRouter).
 */
data class StreamResult(
    val content: String,
    val modelId: String? = null
)
