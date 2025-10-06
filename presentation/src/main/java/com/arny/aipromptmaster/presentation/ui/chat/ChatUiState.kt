package com.arny.aipromptmaster.presentation.ui.chat

import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.LlmModel

/**
 * Данные чата (основное содержимое)
 */
data class ChatData(
    val messages: List<ChatMessage> = emptyList(),
    val selectedModel: LlmModel? = null,
    val systemPrompt: String? = null,
    val conversationId: String? = null
)

/**
 * Состояние генерации ответа (для управления UI)
 */
sealed class ChatUiState {
    object Idle : ChatUiState()

    data class Streaming(val requestId: String) : ChatUiState()

    data class BackgroundStreaming(val requestId: String) : ChatUiState()

    object Completed : ChatUiState()

    object Cancelled : ChatUiState()

    data class RestoredFromBackground(
        val requestId: String,
        val partialResponse: String
    ) : ChatUiState()

    data class Error(val message: String) : ChatUiState()
}

/**
 * UI-представление файла для отображения в виде чипов (chips) в интерфейсе чата.
 *
 * Служит промежуточной моделью между доменной моделью [FileAttachment] и UI,
 * включая информацию о статусе загрузки, форматированное отображение, типе файла и возможных ошибках.
 *
 * @property id уникальный идентификатор файла, соответствует [FileAttachment.id]
 * @property displayName короткое, удобочитаемое имя файла для отображения в UI (например, "notes.txt")
 * @property sizeFormatted размер файла в человекочитаемом формате (например, "1.5 MB", "450 KB")
 * @property size исходный размер файла в байтах (используется для внутренних операций или форматирования)
 * @property fileExtension расширение файла без точки (например, "txt", "pdf", "jpg")
 * @property mimeType MIME-тип файла (например, "text/plain", "application/pdf"); может быть `null`, если неизвестен
 * @property originalContent полное текстовое содержимое файла; по умолчанию пустая строка
 * @property uploadStatus текущий статус обработки файла (ожидание, загрузка, завершено, ошибка и т.д.)
 * @property errorMessage сообщение об ошибке, если обработка файла завершилась неудачно; `null`, если ошибки нет
 */
data class UiAttachment(
    val id: String,
    val displayName: String,
    val sizeFormatted: String = "",
    val size: Long = 0,
    val fileExtension: String = "",
    val mimeType: String? = null,
    val originalContent: String = "",
    val uploadStatus: UploadStatus,
    val errorMessage: String? = null
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}
