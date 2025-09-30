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

                handleDeletedPrompts(remotePrompts)
                promptsRepository.savePrompts(remotePrompts)
                setLastSyncTime(System.currentTimeMillis())
                promptsRepository.invalidateSortDataCache()

                SyncResult.Success(remotePrompts)
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


    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        settingsRepository.getLastSyncTime()
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        settingsRepository.setLastSyncTime(timestamp)
    }
}