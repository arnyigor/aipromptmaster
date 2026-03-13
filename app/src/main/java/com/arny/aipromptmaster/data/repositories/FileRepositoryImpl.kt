package com.arny.aipromptmaster.data.repositories

import android.content.Context
import android.net.Uri
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.models.FileProcessingResult
import com.arny.aipromptmaster.data.utils.FileUtils
import com.arny.aipromptmaster.data.utils.FileUtils.formatFileSize
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.services.FileProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Реализация репозитория файлов.
 *
 * Включены:
 *  • Ограничение размера загружаемого файла (`MAX_FILE_SIZE_BYTES`);
 *  • Проверка размера до начала чтения и при необходимости выдаёт `DomainError.Generic`;
 *  • Корректное обновление `fileSize` в `FileAttachment`;
 *  • Чтение mime‑типа через ContentResolver.
 */
class FileRepositoryImpl(
    private val context: Context,
    private val fileProcessing: FileProcessing
) : IFileRepository {

    companion object {
        /** Максимальный размер файла, допустимый для загрузки. 5 МБ */
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB
    }

    /* Внутреннее хранилище и поток‑наблюдатель. */
    private val files = mutableMapOf<String, FileAttachment>()
    private val filesFlow = MutableStateFlow<List<FileAttachment>>(emptyList())
    private val mutex = Mutex()

    override suspend fun saveTemporaryFile(fileAttachment: FileAttachment): String =
        mutex.withLock {
            files[fileAttachment.id] = fileAttachment
            filesFlow.value = files.values.toList()
            fileAttachment.id
        }

    override suspend fun getTemporaryFile(id: String): FileAttachment? =
        mutex.withLock { files[id] }

    override suspend fun updateTemporaryFile(id: String, updatedContent: String): Boolean =
        mutex.withLock {
            val current = files[id]
            return@withLock if (current != null) {
                val updated = current.copy(originalContent = updatedContent)
                files[id] = updated
                filesFlow.value = files.values.toList()
                true
            } else false
        }

    override suspend fun deleteTemporaryFile(id: String) =
        mutex.withLock {
            files.remove(id)
            filesFlow.value = files.values.toList()
        }

    override fun getAllTemporaryFiles(): Flow<List<FileAttachment>> =
        filesFlow.asStateFlow()

    /**
     * Процесс чтения/обработки файла по `Uri`.
     *
     * 1. Проверяем размер и выбрасываем DomainError.Generic,
     *    если превышает лимит.
     * 2. Создаём временный `FileAttachment` с корректным `fileSize`
     *    и mime‑типом.
     * 3. Запускаем стриминг-обработку через `FileProcessing`.
     */
    override fun processFileFromUri(uri: Uri): Flow<FileProcessingResult> = flow {
        // --- Шаг 1: проверка размера (с возможным броском DomainError.Generic) -------------
        val sizeInBytes = getSizeForUri(uri)          // уже выбрасывает исключение при превышении

        // --- Шаг 2: Создание временного файла --------------------------------------------
        val fileName = FileUtils.getFileName(context, uri)
        val attachment = FileAttachment(
            fileName = fileName,
            fileExtension = FileUtils.getFileExtension(fileName),
            fileSize = sizeInBytes,
            mimeType = context.contentResolver.getType(uri).orEmpty(),
            originalContent = ""
        )
        val fileId = saveTemporaryFile(attachment)

        // --- Шаг 3: Старт обработки ---------------------------------------------------------
        emit(FileProcessingResult.Started(fileName, sizeInBytes))

        // ... потоковое чтение дальше ...
        fileProcessing.processFileFromUriStreaming(uri, chunkSize = 8192)
            .collect { result ->
                when (result) {
                    is FileProcessingResult.Progress -> emit(result)

                    is FileProcessingResult.Complete -> {
                        // сохраняем полученный текст
                        updateTemporaryFile(fileId, result.text)
                        emit(result)
                    }

                    is FileProcessingResult.Error -> emit(result)

                    else -> emit(result)      // в случае Future/Other событий
                }
            }
    }.flowOn(Dispatchers.IO)

    /**
     * Получить размер файла из URI. Если `ContentResolver` не знает длину,
     * читаем поток полностью, но только пока файл находится под лимитом.
     */
    private suspend fun getSizeForUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        // 1️⃣ Получаем длину через AssetFileDescriptor, если доступен.
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val length = afd.length
            if (length > 0L) {
                if (length > MAX_FILE_SIZE_BYTES) {
                    throw DomainError.Generic(
                        StringHolder.Text(
                            context.getString(
                                R.string.error_file_too_big,
                                formatFileSize(MAX_FILE_SIZE_BYTES)
                            )
                        )
                    )
                }
                return@withContext length
            }
        }

        // 2️⃣ Если дескриптор недоступен – читаем поток вручную и сразу останавливаемся после превышения лимита.
        context.contentResolver.openInputStream(uri)?.use { stream ->
            var total = 0L
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break

                total += read.toLong()
                if (total > MAX_FILE_SIZE_BYTES) {
                    throw DomainError.Generic(
                        StringHolder.Text(
                            context.getString(
                                R.string.error_file_too_big,
                                formatFileSize(MAX_FILE_SIZE_BYTES)
                            )
                        )
                    )
                }
            }
            total
        } ?: throw DomainError.Generic("Не удалось открыть поток для URI: $uri")
    }
}

