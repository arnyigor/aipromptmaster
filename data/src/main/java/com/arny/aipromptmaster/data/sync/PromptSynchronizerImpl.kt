package com.arny.aipromptmaster.data.sync

import android.content.Context
import android.util.Log
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.GitHubConfig
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.repositories.PromptsRepositoryImpl
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.SyncResult
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

class PromptSynchronizerImpl @Inject constructor(
    private val githubService: GitHubService,
    private val promptsRepository: PromptsRepositoryImpl,
    private val prefs: Prefs,
    private val gson: Gson,
    private val config: GitHubConfig,
    private val context: Context
) : IPromptSynchronizer {

    companion object {
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
        private const val TAG = "PromptSynchronizer"
    }

    override suspend fun synchronize(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val remotePrompts = mutableListOf<Prompt>()
            val errors = mutableListOf<String>()

            // Загружаем архив репозитория
            val response = githubService.downloadArchive(
                owner = config.owner,
                repo = config.repo,
                ref = config.branch
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download archive: ${response.message()}")
                return@withContext SyncResult.Error("Не удалось загрузить архив репозитория")
            }

            // Создаем временную директорию для распаковки
            val tempDir = createTempDirectory()
            val zipFile = File(tempDir, "repo.zip")

            try {
                // Сохраняем архив
                response.body()?.byteStream()?.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Распаковываем архив
                unzipFile(zipFile, tempDir)

                // Ищем директорию prompts в распакованном архиве
                val promptsDir = findPromptsDirectory(tempDir)
                    ?: return@withContext SyncResult.Error("Директория prompts не найдена в архиве")

                // Обрабатываем JSON файлы рекурсивно
                processDirectory(promptsDir, remotePrompts, errors)

                // Очищаем временные файлы
                tempDir.deleteRecursively()

                if (remotePrompts.isEmpty() && errors.isEmpty()) {
                    Log.w(TAG, "No prompts found in archive")
                    return@withContext SyncResult.Error("Не найдены промпты в репозитории")
                }

                if (errors.isNotEmpty()) {
                    Log.w(TAG, "Sync completed with errors: ${errors.joinToString()}")
                    return@withContext SyncResult.Error(
                        "Синхронизация завершена с ошибками:\n${errors.joinToString("\n")}"
                    )
                }

                // Обрабатываем удаленные промпты
                try {
                    handleDeletedPrompts(remotePrompts)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling deleted prompts", e)
                    return@withContext SyncResult.Error(
                        "Ошибка при обработке удаленных промптов: ${e.message}"
                    )
                }

                // Сохраняем все полученные с сервера промпты.
                // Предполагается, что promptsRepository.savePrompts выполняет "upsert"
                // (обновляет существующие по ID или вставляет новые).
                try {
                    promptsRepository.savePrompts(remotePrompts)
                    setLastSyncTime(System.currentTimeMillis())
                    Log.i(
                        TAG,
                        "Sync completed successfully, processed ${remotePrompts.size} prompts from remote"
                    )
                    // Возвращаем все обработанные удаленные промпты как успешный результат
                    SyncResult.Success(remotePrompts)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving prompts", e)
                    return@withContext SyncResult.Error(
                        "Ошибка при сохранении промптов: ${e.message}"
                    )
                }
            } finally {
                // Убеждаемся, что временные файлы удалены
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            SyncResult.Error("Непредвиденная ошибка: ${e.message}")
        }
    }

    private fun createTempDirectory(): File {
        val tempDir = File(context.cacheDir, "aipromptmaster_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir
    }

    private fun unzipFile(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun findPromptsDirectory(dir: File): File? {
        if (dir.name == "prompts") return dir
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findPromptsDirectory(file)
                if (found != null) return found
            }
        }
        return null
    }

    private fun processDirectory(
        dir: File,
        remotePrompts: MutableList<Prompt>,
        errors: MutableList<String>
    ) {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> {
                    processDirectory(file, remotePrompts, errors)
                }

                file.name.endsWith(".json") -> {
                    try {
                        val jsonContent = file.readText()
                        val promptJson = try {
                            gson.fromJson(jsonContent, PromptJson::class.java)
                        } catch (e: JsonParseException) {
                            Log.e(TAG, "Failed to parse JSON from ${file.name}", e)
                            errors.add("Ошибка парсинга файла ${file.name}: ${e.message}")
                            return@forEach
                        }

                        remotePrompts.add(promptJson.toDomain())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file ${file.name}", e)
                        errors.add("Ошибка обработки файла ${file.name}: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun handleDeletedPrompts(remotePrompts: List<Prompt>) {
        // 1. Получаем локальные промпты
        val localPrompts = promptsRepository.getAllPrompts().first()

        // 2. Создаем Set ID удаленных промптов для быстрого поиска
        val remoteIds = remotePrompts.map { it.id }.toSet()

        // 3. Фильтруем, чтобы найти ID для удаления (ваша логика)
        val idsToDelete = localPrompts
            .filter { !it.isLocal && it.id !in remoteIds }
            .map { it.id } // Сразу преобразуем в список ID

        // 4. Если есть что удалять, выполняем ОДНУ пакетную операцию
        if (idsToDelete.isNotEmpty()) {
            Log.i(TAG, "Deleting ${idsToDelete.size} prompts that are no longer on remote.")
            // Вызываем метод репозитория, который внутри вызовет DAO с пакетным удалением
            promptsRepository.deletePromptsByIds(idsToDelete)
        }
    }

    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        prefs.get<Long>(LAST_SYNC_KEY) ?: 0L
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.put(LAST_SYNC_KEY, timestamp)
    }
} 