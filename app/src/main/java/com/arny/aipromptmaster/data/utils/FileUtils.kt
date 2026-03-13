package com.arny.aipromptmaster.data.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arny.aipromptmaster.domain.models.FileAttachment
import java.text.DecimalFormat
import java.util.UUID

object FileUtils {

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }

    fun getFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        // Fallback к старому методу если DISPLAY_NAME недоступен
                        uri.path?.substringAfterLast('/') ?: "unknown_file"
                    }
                } else {
                    "unknown_file"
                }
            } ?: "unknown_file"
        } catch (e: Exception) {
            // Fallback к старому методу в случае ошибки
            uri.path?.substringAfterLast('/') ?: "unknown_file"
        }
    }

    fun isTextFile(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                mimeType == "application/xml" ||
                mimeType == "application/javascript" ||
                mimeType == "application/x-yaml" ||
                mimeType == "application/x-httpd-php" ||
                mimeType == "application/x-sh"
    }

    fun createFileAttachment(
        context: Context,
        uri: Uri,
        fileName: String,
        content: String
    ): FileAttachment {
        val mimeType = getMimeType(context, uri)
        val fileExtension = getFileExtension(fileName)
        val fileSize = content.toByteArray(Charsets.UTF_8).size.toLong()

        return FileAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            fileExtension = fileExtension,
            fileSize = fileSize,
            mimeType = mimeType,
            originalContent = content,
            isEditable = isEditableFile(fileExtension)
        )
    }

    private fun isEditableFile(extension: String): Boolean {
        return extension.lowercase() !in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf")
    }

    private val SIZE_UNITS = arrayOf(
        SizeUnit("B", 1L),                         // 1024^0
        SizeUnit("KB", 1024L),                     // 1024^1
        SizeUnit("MB", 1024L * 1024L),             // 1024^2
        SizeUnit("GB", 1024L * 1024L * 1024L),     // 1024^3
    )

    private data class SizeUnit(val symbol: String, val bytes: Long)

    /**
     * Форматирует размер в читаемый вид.
     *
     * @param bytes количество байтов. Не может быть отрицательным.
     * @return строка вида «12.3 MB» или «987 B».
     * @throws IllegalArgumentException если `bytes < 0`.
     */
    fun formatFileSize(bytes: Long): String {
        require(bytes >= 0) { "Размер не может быть отрицательным: $bytes" }

        // Находим наибольшую подходящую единицу
        val unit = SIZE_UNITS.lastOrNull { bytes >= it.bytes } ?: SIZE_UNITS[0]

        return if (unit == SIZE_UNITS[0]) {
            "$bytes B"
        } else {
            // Округление до одного знака после запятой
            DecimalFormat("#0.0").format(bytes.toDouble() / unit.bytes) + " ${unit.symbol}"
        }
    }
}