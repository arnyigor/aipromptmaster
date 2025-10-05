package com.arny.aipromptmaster.domain.utils

import android.content.Context
import android.net.Uri
import com.arny.aipromptmaster.domain.models.FileAttachment
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
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}