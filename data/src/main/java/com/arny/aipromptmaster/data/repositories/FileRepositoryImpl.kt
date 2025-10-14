package com.arny.aipromptmaster.data.repositories

import android.content.Context
import android.net.Uri
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import com.arny.aipromptmaster.domain.services.FileProcessingService
import com.arny.aipromptmaster.domain.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class FileRepositoryImpl(
    private val context: Context,
    private val fileProcessingService: FileProcessingService
) : IFileRepository {
    private val files = mutableMapOf<String, FileAttachment>()
    private val filesFlow = MutableStateFlow<List<FileAttachment>>(emptyList())
    private val mutex = Mutex()

    override suspend fun saveTemporaryFile(fileAttachment: FileAttachment): String {
        return mutex.withLock {
            files[fileAttachment.id] = fileAttachment
            filesFlow.value = files.values.toList()
            fileAttachment.id
        }
    }

    override suspend fun getTemporaryFile(id: String): FileAttachment? {
        return mutex.withLock {
            files[id]
        }
    }

    override suspend fun getTemporaryFileContent(id: String): String? {
        return mutex.withLock {
            files[id]?.originalContent
        }
    }

    override suspend fun updateTemporaryFile(id: String, updatedContent: String): Boolean {
        return mutex.withLock {
            files[id]?.let { file ->
                val updatedFile = file.copy(originalContent = updatedContent)
                files[id] = updatedFile
                filesFlow.value = files.values.toList()
                true
            } ?: false
        }
    }

    override suspend fun deleteTemporaryFile(id: String) {
        mutex.withLock {
            files.remove(id)
            filesFlow.value = files.values.toList()
        }
    }

    override fun getAllTemporaryFiles(): Flow<List<FileAttachment>> {
        return filesFlow.asStateFlow()
    }

    override fun processFileFromUri(uri: Uri): Flow<FileProcessingResult> = flow {
        // 1. Создаем временный FileAttachment
        val tempFileName = FileUtils.getFileName(context, uri)
        val tempAttachment = FileAttachment(
            fileName = tempFileName,
            fileExtension = FileUtils.getFileExtension(tempFileName),
            fileSize = 0L,
            mimeType = "",
            originalContent = ""
        )

        // 2. Сохраняем временный файл (suspend вызов)
        val fileId = saveTemporaryFile(tempAttachment)

        // 3. Эмитим событие начала обработки
        emit(FileProcessingResult.Started(tempFileName, 0L))

        // 4. Собираем Flow из сервиса и пробрасываем события
        fileProcessingService.processFileFromUriStreaming(uri, chunkSize = 8192)
            .collect { result ->
                when (result) {
                    is FileProcessingResult.Progress -> {
                        // Пробрасываем прогресс
                        emit(result)
                    }
                    is FileProcessingResult.Complete -> {
                        // Обновляем файл в репозитории (suspend вызов)
                        updateTemporaryFile(fileId, result.text)
                        // Эмитим финальный результат
                        emit(result)
                    }
                    is FileProcessingResult.Error -> {
                        // Пробрасываем ошибку
                        emit(result)
                    }
                    else -> emit(result)
                }
            }
    }.flowOn(Dispatchers.IO)

}