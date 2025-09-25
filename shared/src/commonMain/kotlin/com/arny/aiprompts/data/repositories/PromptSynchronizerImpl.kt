package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.utils.ZipUtils
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.repositories.SyncResult
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.getCacheDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class PromptSynchronizerImpl(
    private val service: GitHubService,
    private val promptsRepository: IPromptsRepository,
    private val settingsRepository: ISettingsRepository,
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

    override suspend fun synchronize(ignoreCooldown: Boolean): SyncResult = withContext(Dispatchers.IO) {
        val lastSync = getLastSyncTime()
        val currentTime = System.currentTimeMillis()

        if (!ignoreCooldown && currentTime - lastSync < SYNC_COOLDOWN_MS) {
            return@withContext SyncResult.TooSoon
        }

        val archiveUrl =
            "https://github.com/arnyigor/aiprompts/releases/download/latest-prompts/prompts.zip"

        runCatching {
            downloadAndProcessArchive(archiveUrl)
        }.fold(
            onSuccess = { remotePrompts ->

                // Побочные эффекты выполнения синхронизации
                handleDeletedPrompts(remotePrompts)
                promptsRepository.savePrompts(remotePrompts)
                setLastSyncTime(System.currentTimeMillis())
                promptsRepository.invalidateSortDataCache()

                SyncResult.Success(remotePrompts)
            },
            onFailure = { e ->
                SyncResult.Error(
                    StringHolder.Text(e.message.orEmpty())
                )
            }
        )
    }

    internal suspend fun downloadAndProcessArchive(url: String): List<Prompt> =
        withContext(Dispatchers.IO) {
            // 1. Скачиваем архив
            val responseBody = service.downloadFile(url)

            // 2. Создаем временную директорию для распаковки
            val tempDir = File(getCacheDir(), "temp_prompts_${System.currentTimeMillis()}")

            try {
                // 3. Распаковываем архив
                val extractResult = ZipUtils.extractZip(responseBody, tempDir)
                extractResult.getOrThrow() // Пробрасываем исключение если распаковка неудачна

                // 4. Читаем JSON файлы из всех категорий
                val jsonFiles = ZipUtils.readJsonFilesFromDirectory(tempDir)

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
                    } catch (_: Exception) {
                    }
                }

                prompts

            } finally {
                // 6. Очищаем временную директорию
                tempDir.deleteRecursively()
            }
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
            // Вызываем метод репозитория, который внутри вызовет DAO с пакетным удалением
            promptsRepository.deletePromptsByIds(idsToDelete)
        }
    }

    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        settingsRepository.getLastSyncTime()
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        settingsRepository.setLastSyncTime(timestamp)
    }
}