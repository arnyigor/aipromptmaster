package com.arny.aipromptmaster.domain.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

class FileProcessingService(private val context: Context) {

    /**
     * Безопасное streaming чтение файла по частям
     * @param chunkSize размер chunk в байтах (8-64KB рекомендуется)
     */
    fun processFileFromUriStreaming(
        uri: Uri,
        chunkSize: Int = 16384 // 16KB по умолчанию
    ): Flow<FileProcessingResult> = flow {
        val fileName = FileUtils.getFileName(context, uri)
        val fileSize = getFileSize(uri)

        emit(FileProcessingResult.Started(fileName, fileSize))

        val contentBuilder = StringBuilder()
        var bytesRead = 0L

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(chunkSize)
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    // Конвертируем chunk в текст
                    val chunkText = String(buffer, 0, read, Charsets.UTF_8)
                    contentBuilder.append(chunkText)

                    bytesRead += read
                    val progress = if (fileSize > 0) {
                        (bytesRead.toFloat() / fileSize * 100).toInt()
                    } else {
                        0
                    }

                    // Эмитим прогресс с частичными данными
                    val currentText = contentBuilder.toString()
                    val preview = if (currentText.length > 300) {
                        currentText.takeLast(300)
                    } else {
                        currentText
                    }

                    emit(FileProcessingResult.Progress(
                        progress = progress,
                        bytesRead = bytesRead,
                        totalBytes = fileSize,
                        previewText = preview
                    ))
                }

                // Финальный результат
                val finalText = contentBuilder.toString()
                val fileAttachment = FileUtils.createFileAttachment(
                    context = context,
                    uri = uri,
                    fileName = fileName,
                    content = finalText
                )

                emit(FileProcessingResult.Complete(finalText, fileAttachment))
            } ?: throw IOException("Не удалось открыть файл")
        } catch (e: Exception) {
            emit(FileProcessingResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getFileSize(uri: Uri): Long = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) cursor.getLong(sizeIndex) else -1L
            } else -1L
        } ?: -1L
    }
}

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