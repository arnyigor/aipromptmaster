package com.arny.aipromptmaster.data.sync

import android.content.Context
import android.util.Log
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.utils.ZipUtils
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject

class PromptSynchronizerImpl @Inject constructor(
    private val service: GitHubService,
    private val promptsRepository: IPromptsRepository,
    private val prefs: Prefs,
    private val context: Context
) : IPromptSynchronizer {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    companion object {
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
        private const val TAG = "PromptSynchronizer"

        // Устанавливаем минимальный интервал между синхронизациями (например, 60 минут)
        private const val SYNC_COOLDOWN_MS = 60 * 60 * 1000
    }

    override suspend fun synchronize(): SyncResult = withContext(Dispatchers.IO) {
        val lastSync = getLastSyncTime()
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSync < SYNC_COOLDOWN_MS) {
            Log.d(TAG, "Sync attempt is too soon. Cooldown active.")
            return@withContext SyncResult.TooSoon
        }

        val archiveUrl =
            "https://github.com/arnyigor/aiprompts/releases/download/latest-prompts/prompts.zip"

        runCatching {
            Log.d(TAG, "Starting sync: downloading archive from $archiveUrl")
            downloadAndProcessArchive(archiveUrl)
        }.fold(
            onSuccess = { remotePrompts ->
                Log.d(TAG, "Successfully processed ${remotePrompts.size} prompts from archive.")

                // Побочные эффекты выполнения синхронизации
                handleDeletedPrompts(remotePrompts)
                promptsRepository.savePrompts(remotePrompts)
                setLastSyncTime(System.currentTimeMillis())
                promptsRepository.invalidateSortDataCache()

                SyncResult.Success(remotePrompts)
            },
            onFailure = { e ->
                val errorMsg = "Sync failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                SyncResult.Error(
                    StringHolder.Formatted(
                        R.string.unknown_error,
                        listOf(e.message.orEmpty())
                    )
                )
            }
        )
    }

    private suspend fun downloadAndProcessArchive(url: String): List<Prompt> =
        withContext(Dispatchers.IO) {
            // 1. Скачиваем архив
            val response = service.downloadFile(url)

            if (!response.isSuccessful) {
                throw IOException("Failed to download archive. Code: ${response.code()}, Message: ${response.message()}")
            }

            val responseBody = response.body()
                ?: throw IOException("Archive response body is null")

            // 2. Создаем временную директорию для распаковки
            val tempDir = File(context.cacheDir, "temp_prompts_${System.currentTimeMillis()}")

            try {
                // 3. Распаковываем архив
                val extractResult = ZipUtils.extractZip(responseBody.byteStream(), tempDir)
                extractResult.getOrThrow() // Пробрасываем исключение если распаковка неудачна

                Log.d(TAG, "Archive extracted to: ${tempDir.absolutePath}")

                // 4. Читаем JSON файлы из всех категорий
                val jsonFiles = ZipUtils.readJsonFilesFromDirectory(tempDir)
                Log.d(TAG, "Found ${jsonFiles.size} JSON files")

                // 5. Десериализуем и конвертируем в доменные модели
                val prompts = mutableListOf<Prompt>()

                jsonFiles.forEach { (category, jsonContent) ->
                    try {
                        val promptJson = json.decodeFromString<PromptJson>(jsonContent)
                        // Устанавливаем категорию если она не указана в JSON
                        if (promptJson.category.isNullOrBlank()) {
                            promptJson.category = category
                        }
                        prompts.add(promptJson.toDomain())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse JSON from category '$category': ${e.message}")
                        // Продолжаем обработку остальных файлов
                    }
                }

                prompts

            } finally {
                // 6. Очищаем временную директорию
                tempDir.deleteRecursively()
                Log.d(TAG, "Temporary directory cleaned up")
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

    private suspend fun handleDeletedPrompts(prompts: List<Prompt>) {
        // 1. Получаем локальные промпты
        val localPrompts = promptsRepository.getAllPrompts().first()

        // 2. Создаем Set ID удаленных промптов для быстрого поиска
        val remoteIds = prompts.map { it.id }.toSet()

        // 3. Фильтруем, чтобы найти ID для удаления (ваша логика)
        val idsToDelete = localPrompts
            .filter { !it.isLocal && it.id !in remoteIds }
            .map { it.id } // Сразу преобразуем в список ID

        // 4. Если есть что удалять, выполняем ОДНУ пакетную операцию
        if (idsToDelete.isNotEmpty()) {
//            Log.i(TAG, "Deleting ${idsToDelete.size} prompts that are no longer on remote.")
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