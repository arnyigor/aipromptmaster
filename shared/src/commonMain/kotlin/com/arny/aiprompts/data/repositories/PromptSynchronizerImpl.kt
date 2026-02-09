@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.utils.ZipUtils
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.repositories.SyncResult
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.getCacheDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.ExperimentalTime

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
        val promptsCount = promptsRepository.getPromptsCount()
        val lastSync = getLastSyncTime()
        val currentTime = System.currentTimeMillis()

        println("✅ [PromptSync] synchronize promptsCount:$promptsCount, ignoreCooldown:$ignoreCooldown, currentTime:$currentTime, lastSync:$lastSync, SYNC_COOLDOWN_MS:$SYNC_COOLDOWN_MS")

        // Проверяем cooldown только если ignoreCooldown = false
        if (promptsCount > 0 && !ignoreCooldown && currentTime - lastSync < SYNC_COOLDOWN_MS) {
            println("⏳ [PromptSync] Sync skipped - cooldown active")
            return@withContext SyncResult.TooSoon
        }

        val archiveUrl = "https://github.com/arnyigor/aiprompts/releases/download/latest-prompts/prompts.zip"

        runCatching {
            downloadAndProcessArchive(archiveUrl)
        }.fold(
            onSuccess = { remotePrompts ->
                println("✅ [PromptSync] Downloaded ${remotePrompts.size} prompts")

                // ✅ Проверка: если загрузилось слишком мало
                if (remotePrompts.size < 100) {
                    println("⚠️ [PromptSync] Warning: Only ${remotePrompts.size} prompts loaded")
                }

                val uniquePrompts = deduplicatePrompts(remotePrompts)
                println("✅ [PromptSync] After deduplication: ${uniquePrompts.size} prompts (removed ${remotePrompts.size - uniquePrompts.size} duplicates)")

                handleDeletedPrompts(uniquePrompts)
                promptsRepository.savePrompts(uniquePrompts)
                setLastSyncTime(System.currentTimeMillis())
                promptsRepository.invalidateSortDataCache()

                SyncResult.Success(uniquePrompts)
            },
            onFailure = { e ->
                println("❌ [PromptSync] Sync failed: ${e.message}")
                e.printStackTrace()
                SyncResult.Error(StringHolder.Text(e.message.orEmpty()))
            }
        )
    }


    internal suspend fun downloadAndProcessArchive(url: String): List<Prompt> =
        withContext(Dispatchers.IO) {
            val responseBody = service.downloadFile(url)
            val tempDir = File(getCacheDir(), "temp_prompts_${System.currentTimeMillis()}")

            try {
                println("✅ [PromptSync] downloadAndProcessArchive synced Размер: ${responseBody.size} байт")
                val extractResult = ZipUtils.extractZip(responseBody, tempDir)
                extractResult.getOrThrow()

                val jsonFiles = ZipUtils.readJsonFilesFromDirectory(tempDir)
                println("✅ [PromptSync] readJsonFilesFromDirectory returned ${jsonFiles.size} files")

                val prompts = mutableListOf<Prompt>()
                var successCount = 0
                var errorCount = 0

                // ✅ ТЕПЕРЬ uniqueKey = "category/filename"
                jsonFiles.forEach { (uniqueKey, jsonContent) ->
                    try {
                        val promptJson = json.decodeFromString<PromptJson>(jsonContent)

                        // Извлекаем категорию из ключа
                        val category = uniqueKey.substringBefore("/")
                        if (promptJson.category.isNullOrBlank()) {
                            promptJson.category = category
                        }

                        prompts.add(promptJson.toDomain())
                        successCount++
                    } catch (e: Exception) {
                        println("❌ [PromptSync] Failed to parse '$uniqueKey': ${e.message}")
                        e.printStackTrace()
                        errorCount++
                    }
                }

                println("✅ [PromptSync] Parsed $successCount prompts, failed $errorCount")

                if (successCount < 100) {
                    println("⚠️ [PromptSync] WARNING: Expected ~800 prompts, got only $successCount")
                }

                prompts

            } finally {
                tempDir.deleteRecursively()
            }
        }

    private suspend fun handleDeletedPrompts(prompts: List<Prompt>) {
        val localPrompts = promptsRepository.getAllPrompts().first()
        val remoteIds = prompts.map { it.id }.toSet()

        val idsToDelete = localPrompts
            .filter { !it.isLocal && it.id !in remoteIds }
            .map { it.id }

        if (idsToDelete.isNotEmpty()) {
            println("🗑️ [PromptSync] Deleting ${idsToDelete.size} obsolete prompts")
            promptsRepository.deletePromptsByIds(idsToDelete)
        }
    }

    private suspend fun deduplicatePrompts(prompts: List<Prompt>): List<Prompt> {
        val existingPrompts = promptsRepository.getAllPrompts().first()
        val existingNonLocalTitles = existingPrompts
            .filter { !it.isLocal }
            .associateBy { it.title.lowercase().trim() }

        return prompts.filter { remotePrompt ->
            val normalizedTitle = remotePrompt.title.lowercase().trim()
            normalizedTitle !in existingNonLocalTitles
        }
    }

    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        settingsRepository.getLastSyncTime()
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        settingsRepository.setLastSyncTime(timestamp)
    }

    /**
     * Загружает локальные файлы промптов из папки prompts/ в базу данных.
     * Вызывается при старте приложения для загрузки промптов, созданных через Importer.
     */
    override suspend fun loadLocalPrompts(): SyncResult = withContext(Dispatchers.IO) {
        runCatching {
            val localPrompts = loadPromptsFromLocalDirectory()
            if (localPrompts.isNotEmpty()) {
                // Перед сохранением проверяем дедупликацию с учётом isLocal флага
                val mergedPrompts = mergeWithExistingPrompts(localPrompts)
                promptsRepository.savePrompts(mergedPrompts)
                println("✅ [PromptSync] Загружено ${mergedPrompts.size} локальных промптов (из ${localPrompts.size} файлов)")
            } else {
                println("ℹ️ [PromptSync] Локальные промпты не найдены")
            }
        }.fold(
            onSuccess = { SyncResult.Success(emptyList()) },
            onFailure = { e ->
                println("❌ [PromptSync] Ошибка загрузки локальных промптов: ${e.message}")
                e.printStackTrace()
                SyncResult.Error(StringHolder.Text(e.message.orEmpty()))
            }
        )
    }

    /**
     * Загружает все JSON файлы промптов из локальной папки prompts/
     */
    private suspend fun loadPromptsFromLocalDirectory(): List<Prompt> = withContext(Dispatchers.IO) {
        val rootDir = findProjectRootDir()
        if (rootDir == null) {
            println("⚠️ [PromptSync] Не удалось найти корневую директорию проекта")
            return@withContext emptyList()
        }

        val promptsDir = File(rootDir, "prompts")
        if (!promptsDir.exists() || !promptsDir.isDirectory) {
            println("⚠️ [PromptSync] Папка prompts/ не найдена: ${promptsDir.absolutePath}")
            return@withContext emptyList()
        }

        val prompts = mutableListOf<Prompt>()
        var successCount = 0
        var errorCount = 0

        promptsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                try {
                    val jsonContent = file.readText()
                    val promptJson = json.decodeFromString<PromptJson>(jsonContent)
                    val prompt = promptJson.toDomain().copy(isLocal = true)
                    prompts.add(prompt)
                    successCount++
                } catch (e: Exception) {
                    println("⚠️ [PromptSync] Ошибка чтения ${file.name}: ${e.message}")
                    errorCount++
                }
            }

        println("✅ [PromptSync] Просканировано: $successCount файлов, ошибок: $errorCount")
        prompts
    }

    /**
     * Находит корневую директорию проекта (где лежит .git)
     */
    private fun findProjectRootDir(): File? {
        var currentDir = File(System.getProperty("user.dir"))
        repeat(10) {
            if (File(currentDir, ".git").exists()) {
                return currentDir
            }
            if (currentDir.parentFile == null) {
                return null
            }
            currentDir = currentDir.parentFile
        }
        return null
    }

    /**
     * Объединяет новые локальные промпты с существующими, защищая локальные от перезаписи
     */
    private suspend fun mergeWithExistingPrompts(newPrompts: List<Prompt>): List<Prompt> {
        val existingPrompts = promptsRepository.getAllPrompts().first()
        val existingById = existingPrompts.associateBy { it.id }

        return newPrompts.map { newPrompt ->
            val existing = existingById[newPrompt.id]
            when {
                // Если промпт уже существует и он локальный - НЕ перезаписываем
                existing != null && existing.isLocal -> {
                    existing
                }
                // Если существующий промпт избранный - сохраняем избранность
                existing != null && existing.isFavorite -> {
                    newPrompt.copy(isFavorite = true)
                }
                // Если промпт уже есть (non-local) - обновляем данными из файла
                existing != null -> {
                    newPrompt
                }
                // Новый промпт - добавляем как есть
                else -> newPrompt
            }
        }
    }
}