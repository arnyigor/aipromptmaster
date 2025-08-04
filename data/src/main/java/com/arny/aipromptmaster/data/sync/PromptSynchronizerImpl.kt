package com.arny.aipromptmaster.data.sync

import android.util.Log
import com.arny.aipromptmaster.data.api.VercelApiService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.prefs.Prefs
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
import javax.inject.Inject

class PromptSynchronizerImpl @Inject constructor(
    private val vercelApiService: VercelApiService,
    private val promptsRepository: IPromptsRepository,
    private val prefs: Prefs
) : IPromptSynchronizer {

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
            SyncResult.TooSoon
        } else {
            runCatching {
                Log.d(TAG, "Starting sync from Vercel API...")
                vercelApiService.getPrompts()
            }.fold(
                onSuccess = { response ->
                    if (response.isSuccessful) {
                        response.body()?.let { remotePromptsJson ->
                            Log.d(TAG, "Successfully fetched ${remotePromptsJson.size} prompts from API.")
                            val remotePrompts = remotePromptsJson.map { it.toDomain() }

                            // Побочные эффекты выполнения синхронизации
                            handleDeletedPrompts(remotePrompts)
                            promptsRepository.savePrompts(remotePrompts)
                            setLastSyncTime(System.currentTimeMillis())
                            promptsRepository.invalidateSortDataCache()

                            SyncResult.Success(remotePrompts)
                        } ?: SyncResult.Error(
                            StringHolder.Text("API response body is null")
                        ).also { Log.e(TAG, it.toString()) }
                    } else {
                        val errorMsg = "Failed to sync. Code: ${response.code()}, Message: ${response.message()}"
                        Log.e(TAG, errorMsg)
                        SyncResult.Error(StringHolder.Text(errorMsg))
                    }
                },
                onFailure = { e ->
                    SyncResult.Error(
                        StringHolder.Formatted(
                            R.string.unknown_error,
                            listOf(e.message.orEmpty())
                        )
                    ).also { Log.e(TAG, "Sync failed", e) }
                }
            )
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