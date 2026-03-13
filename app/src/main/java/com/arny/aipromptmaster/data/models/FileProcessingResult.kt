package com.arny.aipromptmaster.data.models

import com.arny.aipromptmaster.domain.models.FileAttachment

/**
 * Результаты streaming обработки файлов
 */
sealed class FileProcessingResult {
    data class Started(val fileName: String, val fileSize: Long) : FileProcessingResult()

    data class Progress(
        val progress: Int,
        val bytesRead: Long,
        val totalBytes: Long,
        val previewText: String? = null
    ) : FileProcessingResult()

    data class Complete(
        val text: String,
        val fileAttachment: FileAttachment
    ) : FileProcessingResult()

    data class Error(val message: String) : FileProcessingResult()
}