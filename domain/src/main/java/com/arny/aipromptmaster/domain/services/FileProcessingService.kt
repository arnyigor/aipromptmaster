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

/**
 * Сервис для пошаговой (streaming) обработки файлов из URI с поддержкой прогресса и превью.
 *
 * Позволяет читать большие файлы по частям, не загружая их целиком в память,
 * и эмитировать промежуточные результаты для обновления UI.
 *
 * @property context контекст Android для доступа к ContentResolver и другим системным сервисам
 */
class FileProcessingService(private val context: Context) {

    /**
     * Обрабатывает файл из URI с пошаговым чтением (streaming) и возвращает поток результатов.
     *
     * Поддерживает отображение прогресса загрузки и динамического превью содержимого.
     * Чтение выполняется в фоновом потоке ввода-вывода ([Dispatchers.IO]).
     *
     * @param uri URI файла, полученный, например, через Intent или Storage Access Framework
     * @param chunkSize размер буфера чтения в байтах (рекомендуется 8–64 КБ; по умолчанию 16 КБ)
     * @return [Flow] последовательности [FileProcessingResult], отражающих этапы обработки:
     *         [Started] → один или несколько [Progress] → [Complete] или [Error]
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

    /**
     * Асинхронно получает размер файла по его URI через ContentResolver.
     *
     * Использует системный столбец [OpenableColumns.SIZE].
     *
     * @param uri URI файла
     * @return размер файла в байтах, или `-1`, если размер недоступен
     */
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
 * Результат пошаговой обработки файла (например, чтение из URI).
 *
 * Используется для отображения прогресса и превью в UI.
 */
sealed class FileProcessingResult {

    /**
     * Начало обработки файла.
     *
     * @property fileName имя файла
     * @property fileSize размер в байтах
     */
    data class Started(val fileName: String, val fileSize: Long) : FileProcessingResult()

    /**
     * Промежуточный прогресс чтения файла.
     *
     * @property progress процент завершения (0–100)
     * @property bytesRead количество прочитанных байт
     * @property totalBytes общий размер файла
     * @property previewText опциональное текстовое превью (например, последние 300 символов текущего текста)
     */
    data class Progress(
        val progress: Int,
        val bytesRead: Long,
        val totalBytes: Long,
        val previewText: String? = null
    ) : FileProcessingResult()

    /**
     * Файл успешно обработан.
     *
     * @property text полное текстовое содержимое файла
     * @property fileAttachment полная модель файла для сохранения
     */
    data class Complete(
        val text: String,
        val fileAttachment: FileAttachment
    ) : FileProcessingResult()

    /**
     * Произошла ошибка при обработке файла.
     *
     * @property message описание ошибки
     */
    data class Error(val message: String) : FileProcessingResult()
}