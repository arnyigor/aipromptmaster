package com.arny.aiprompts.domain.files

import com.arny.aiprompts.data.model.AttachmentType
import com.arny.aiprompts.data.model.ContentPartImage
import com.arny.aiprompts.data.model.ContentPartText
import com.arny.aiprompts.data.model.ImageUrlData
import com.arny.aiprompts.data.model.MessageAttachment
import com.arny.aiprompts.utils.Logger

/**
 * Процессор для подготовки файлов к отправке в LLM API.
 * Обрабатывает текстовые файлы и изображения, конвертируя их в нужный формат.
 *
 * @property platformFileHandler Platform-specific обработчик файлов
 */
class FilePromptProcessor(
    val platformFileHandler: PlatformFileHandler
) {
    /**
     * Подготавливает контент сообщения с вложениями для API.
     *
     * @param text Текст сообщения пользователя
     * @param attachments Список вложений
     * @return Список частей контента (текст + изображения)
     */
    suspend fun prepareMessageContent(
        text: String,
        attachments: List<MessageAttachment>
    ): List<Any> {
        val parts = mutableListOf<Any>()

        // 1. Обрабатываем текстовые файлы - встраиваем их содержимое в текст
        val textContent = buildString {
            if (text.isNotBlank()) {
                append(text)
            }

            val textAttachments = attachments.filter {
                it.type == AttachmentType.TEXT_FILE || it.type == AttachmentType.CODE
            }

            textAttachments.forEach { attachment ->
                try {
                    if (isNotEmpty()) append("\n\n")
                    append("--- File: ${attachment.getFileName()} ---\n")
                    append(platformFileHandler.readText(attachment.uri))
                    append("\n---")
                } catch (e: Exception) {
                    Logger.e(e, "FilePromptProcessor", "Failed to read text file: ${attachment.uri}")
                    append("\n[Error reading file: ${attachment.getFileName()}]")
                }
            }
        }

        if (textContent.isNotEmpty()) {
            parts.add(ContentPartText(text = textContent))
        }

        // 2. Обрабатываем изображения - конвертируем в Base64
        val imageAttachments = attachments.filter { it.type == AttachmentType.IMAGE }
        imageAttachments.forEach { image ->
            try {
                val base64 = platformFileHandler.readImageToBase64(image.uri)
                val mimeType = image.mimeType ?: "image/jpeg"
                val dataUrl = "data:$mimeType;base64,$base64"

                parts.add(ContentPartImage(imageUrl = ImageUrlData(url = dataUrl)))
                Logger.d("FilePromptProcessor", "Processed image: ${image.getFileName()}")
            } catch (e: Exception) {
                Logger.e(e, "FilePromptProcessor", "Failed to process image: ${image.uri}")
            }
        }

        return parts
    }

    /**
     * Проверяет, требуется ли мультимодальный формат для сообщения.
     */
    fun isMultimodal(attachments: List<MessageAttachment>): Boolean {
        return attachments.any { it.type == AttachmentType.IMAGE }
    }

    /**
     * Копирует вложения во внутреннее хранилище для долгосрочного хранения.
     * Важно: URI из FilePicker могут стать недоступными после перезапуска.
     *
     * @param attachments Список вложений для копирования
     * @return Список вложений с обновленными URI (указывают на внутреннее хранилище)
     */
    suspend fun copyAttachmentsToInternalStorage(
        attachments: List<MessageAttachment>
    ): List<MessageAttachment> {
        return attachments.map { attachment ->
            try {
                // Проверяем, уже ли файл во внутреннем хранилище
                if (isInInternalStorage(attachment.uri)) {
                    return@map attachment
                }

                // Копируем файл
                val fileName = attachment.getFileName()
                    ?: "attachment_${System.currentTimeMillis()}"
                val newUri = platformFileHandler.copyToInternalStorage(
                    attachment.uri,
                    fileName
                )

                attachment.copy(uri = newUri)
            } catch (e: Exception) {
                Logger.e(e, "FilePromptProcessor", "Failed to copy attachment: ${attachment.uri}")
                attachment
            }
        }
    }

    /**
     * Проверяет, находится ли файл уже во внутреннем хранилище приложения.
     */
    private fun isInInternalStorage(uriString: String): Boolean {
        return uriString.contains("/files/attachments/") ||
               uriString.contains("/aiprompts/attachments/")
    }

    /**
     * Получает имя файла из вложения.
     */
    private fun MessageAttachment.getFileName(): String? {
        return platformFileHandler.getFileName(uri)
    }

    /**
     * Получает размер всех вложений в байтах.
     */
    fun getTotalAttachmentsSize(attachments: List<MessageAttachment>): Long {
        return attachments.sumOf { platformFileHandler.getFileSize(it.uri) }
    }

    /**
     * Фильтрует вложения, оставляя только существующие файлы.
     */
    fun filterExistingAttachments(attachments: List<MessageAttachment>): List<MessageAttachment> {
        return attachments.filter { platformFileHandler.fileExists(it.uri) }
    }

    companion object {
        // Максимальный размер изображения для отправки (в байтах) - ~5MB
        const val MAX_IMAGE_SIZE = 5 * 1024 * 1024

        // Максимальный размер текстового файла (в байтах) - ~100KB
        const val MAX_TEXT_FILE_SIZE = 100 * 1024
    }
}
