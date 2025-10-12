package com.arny.aipromptmaster.domain.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arny.aipromptmaster.domain.models.FileAttachment
import java.util.UUID

/**
 * Утилитарные методы для работы с файлами
 */
object FileUtils {

    /**
     * Форматирует размер файла в читаемый вид
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Определяет расширение файла для markdown подсветки синтаксиса
     */
    fun getFileExtensionForMarkdown(extension: String): String {
        return when (extension.lowercase()) {
            "kt", "kotlin" -> "kotlin"
            "java" -> "java"
            "js", "javascript" -> "javascript"
            "ts", "typescript" -> "typescript"
            "py", "python" -> "python"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md", "markdown" -> "markdown"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "less" -> "less"
            "php" -> "php"
            "rb", "ruby" -> "ruby"
            "go" -> "go"
            "rs", "rust" -> "rust"
            "cpp", "c++", "cxx", "cc" -> "cpp"
            "c" -> "c"
            "cs", "csharp" -> "csharp"
            "swift" -> "swift"
            "sh", "bash", "shell" -> "bash"
            "sql" -> "sql"
            "dockerfile" -> "dockerfile"
            "makefile", "mk" -> "makefile"
            "ini", "conf", "config" -> "ini"
            "properties", "prop" -> "properties"
            "toml" -> "toml"
            "gradle" -> "gradle"
            "kts" -> "kotlin"
            else -> "" // Без подсветки для неизвестных типов
        }
    }

    /**
     * Обрезает текст на границе слова
     */
    fun truncateAtWordBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val truncated = text.take(maxLength)
        val lastSpace = truncated.lastIndexOf(' ')

        return if (lastSpace > maxLength * 0.8) {
            truncated.substring(0, lastSpace)
        } else {
            truncated
        }
    }

    /**
     * Получает имя файла из URI
     */
    fun getFileName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "unknown_file"
    }

    /**
     * Получает расширение файла из имени файла
     */
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }

    /**
     * Создает FileAttachment из контекста, URI и содержимого
     */
    fun createFileAttachment(
        context: Context,
        uri: Uri,
        fileName: String,
        content: String
    ): FileAttachment {
        val fileSize = getFileSize(context, uri)
        val mimeType = getMimeType(context, uri)
        val extension = getFileExtension(fileName)

        return FileAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            fileExtension = extension,
            fileSize = fileSize,
            mimeType = mimeType,
            originalContent = content,
            isEditable = true
        )
    }

    /**
     * Получает размер файла из URI
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
            } else 0L
        } ?: 0L
    }

    /**
     * Получает MIME тип файла из URI
     */
    private fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }
}