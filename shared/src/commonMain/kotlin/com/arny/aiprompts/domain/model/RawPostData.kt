package com.arny.aiprompts.domain.model

import kotlinx.datetime.Instant

/**
 * Data-класс для хранения "сырых", минимально обработанных данных,
 * извлеченных из одного HTML-элемента поста.
 * Эта структура используется для передачи данных в UI для верификации.
 */
data class RawPostData(
    val postId: String,
    val author: Author,
    val date: Instant,              // Дата создания поста
    val updatedDate: Instant? = null, // Дата последнего обновления поста
    val fullHtmlContent: String,    // Весь внутренний HTML <div class="postcolor">
    val isLikelyPrompt: Boolean,    // Флаг-подсказка от "детектора"
    val postUrl: String? = null,     // URL на страницу поста
    val fileAttachmentUrl: String? = null, // URL на .txt вложение, если есть (для обратной совместимости)
    val attachments: List<FileAttachment> = emptyList(), // Список всех вложений
    val variables: List<String> = emptyList(), // при импорте пустые
)

/**
 * Модель для файлов-вложений в посте
 */
data class FileAttachment(
    val url: String,           // Полный URL файла
    val filename: String,      // Имя файла
    val fileSize: String? = null, // Размер файла (если указан)
    val fileType: FileType     // Тип файла
)

/**
 * Типы файлов-вложений
 */
enum class FileType {
    TEXT,       // .txt файлы
    IMAGE,      // .jpg, .png, .gif и т.д.
    DOCUMENT,   // .pdf, .doc, .docx и т.д.
    ARCHIVE,    // .zip, .rar, .7z и т.д.
    OTHER       // Другие типы
}