package com.arny.aipromptmaster.data.sync

import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.GitHubConfig
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.repositories.PromptsRepositoryImpl
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.Date
import javax.inject.Inject

class PromptSynchronizerImpl @Inject constructor(
    private val githubService: GitHubService,
    private val promptsRepository: PromptsRepositoryImpl,
    private val prefs: Prefs,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
    private val config: GitHubConfig
) : IPromptSynchronizer {

    companion object {
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
    }

    override suspend fun synchronize(): IPromptSynchronizer.SyncResult = withContext(Dispatchers.IO) {
        try {
            // Получаем список файлов из репозитория
            val response = githubService.getContents(config.owner, config.repo, config.promptsPath)
            if (!response.isSuccessful) {
                return@withContext IPromptSynchronizer.SyncResult.Error(
                    "Failed to fetch contents: ${response.message()}"
                )
            }

            val contents = response.body() ?: return@withContext IPromptSynchronizer.SyncResult.Error("Empty response")
            val jsonFiles = contents.filter { it.name.endsWith(".json") }

            // Загружаем и обрабатываем каждый файл
            val remotePrompts = mutableListOf<Prompt>()
            val conflicts = mutableListOf<SyncConflict>()

            for (file in jsonFiles) {
                val downloadUrl = file.downloadUrl ?: continue
                val jsonContent = downloadFile(downloadUrl)
                val promptJson = gson.fromJson(jsonContent, PromptJson::class.java)
                val remotePrompt = promptJson.toDomain()
                remotePrompts.add(remotePrompt)

                // Проверяем конфликты
                val localPrompt = promptsRepository.getPromptById(remotePrompt.id)
                if (localPrompt != null) {
                    val conflict = checkConflict(localPrompt, remotePrompt)
                    if (conflict != null) {
                        conflicts.add(conflict)
                        continue
                    }
                }
            }

            // Обрабатываем удаленные промпты
            handleDeletedPrompts(remotePrompts)

            // Сохраняем промпты без конфликтов
            val promptsToSave = remotePrompts.filter { prompt ->
                conflicts.none { conflict ->
                    when (conflict) {
                        is SyncConflict.LocalNewer -> conflict.remote.id == prompt.id
                        is SyncConflict.RemoteNewer -> conflict.remote.id == prompt.id
                        is SyncConflict.ContentMismatch -> conflict.remote.id == prompt.id
                    }
                }
            }
            promptsRepository.savePrompts(promptsToSave)

            // Обновляем время последней синхронизации
            setLastSyncTime(Date().time)

            if (conflicts.isEmpty()) {
                IPromptSynchronizer.SyncResult.Success(promptsToSave)
            } else {
                IPromptSynchronizer.SyncResult.Conflicts(conflicts)
            }
        } catch (e: Exception) {
            IPromptSynchronizer.SyncResult.Error("Sync failed: ${e.message}")
        }
    }

    private fun checkConflict(local: Prompt, remote: Prompt): SyncConflict? {
        return when {
            local.modifiedAt > remote.modifiedAt -> SyncConflict.LocalNewer(local, remote)
            local.modifiedAt < remote.modifiedAt -> SyncConflict.RemoteNewer(local, remote)
            local.content != remote.content -> SyncConflict.ContentMismatch(local, remote)
            else -> null
        }
    }

    private suspend fun handleDeletedPrompts(remotePrompts: List<Prompt>) {
        val localPrompts = promptsRepository.getAllPrompts().first()
        val remoteIds = remotePrompts.map { it.id }.toSet()
        val deletedPrompts = localPrompts.filter {
            !it.isLocal && it.id !in remoteIds
        }
        for (prompt in deletedPrompts) {
            promptsRepository.deletePrompt(prompt.id)
        }
    }

    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        prefs.get<Long>(LAST_SYNC_KEY) ?: 0L
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.put(LAST_SYNC_KEY, timestamp)
    }

    private suspend fun downloadFile(url: String): String = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to download file: ${response.message}")
        }

        response.body?.string() ?: throw IOException("Empty response body")
    }
} 