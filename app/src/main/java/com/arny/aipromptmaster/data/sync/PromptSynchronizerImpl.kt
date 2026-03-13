package com.arny.aipromptmaster.data.sync

import android.content.Context
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.utils.ZipUtils
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncStatus
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Синхронизирует подсказки из zip‑архива GitHub release.
 *
 * Гарантии:
 *   • Только один sync выполняется за раз (Mutex).
 *   • Операции БД атомарны (Room транзакция).
 *   • Прогресс доступен через [status].
 *   • Ошибки преобразуются в отдельные DomainError.
 */
class PromptSynchronizerImpl(
    private val service: GitHubService,
    private val promptsRepository: IPromptsRepository,
    private val prefs: Prefs,
    private val context: Context
) : IPromptSynchronizer {
    /** Предотвращает одновременные sync‑ы. */
    private val syncMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    companion object {
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
        private const val TAG = "PromptSynchronizer"

        // 60 минут между sync‑ами – можно сделать configurable
        private const val SYNC_COOLDOWN_MS = 60 * 60 * 1000L

        private const val ARCHIVE_URL =
            "https://github.com/arnyigor/aiprompts/releases/download/latest-prompts/prompts.zip"
    }

    /**
     * Реактивный статус для UI.
     *
     * @see SyncStatus
     */
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.None)

    override val status: StateFlow<SyncStatus> get() = _status

    override suspend fun synchronize(): SyncResult =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {

                // 1️⃣ Проверяем, есть ли локальные промпты
                val localPrompts = promptsRepository.observeAllPrompts().first()
                if (localPrompts.isEmpty()) {
                    Timber.tag(TAG)
                        .d("Локальные промпты отсутствуют, сбрасываем время синхронизации")
                    setLastSyncTime(0L)
                }

                // 2️⃣ Быстрая проверка cooldown
                val lastSync = getLastSyncTime()
                val now = System.currentTimeMillis()

                if (now - lastSync < SYNC_COOLDOWN_MS) {
                    Timber.tag(TAG).d("Попытка синхронизации слишком ранняя. Cooldown активен.")
                    _status.value = SyncStatus.Cooldown
                    return@withContext SyncResult.TooSoon
                }

                // 3️⃣ Эмитируем прогресс
                _status.value = SyncStatus.InProgress

                try {
                    val remotePrompts = downloadAndProcessArchive()
                        .also { _status.value = SyncStatus.InProgress }

                    val ids = handleDeletedPrompts(remotePrompts)
                        .also { _status.value = SyncStatus.InProgress }

                    // 4️⃣ Сохраняем в одной транзакции
                    promptsRepository.syncPrompts(remotePrompts, ids)

                    setLastSyncTime(now)
                    promptsRepository.invalidateSortDataCache()

                    _status.value = SyncStatus.Success(updatedCount = remotePrompts.size)
                    SyncResult.Success(remotePrompts)
                } catch (e: IOException) {
                    Timber.tag(TAG).e(e, "Network error during sync")
                    val err =
                        DomainError.Network(e.localizedMessage ?: R.string.unknown_error.toString())
                    _status.value = SyncStatus.Error(err)
                    SyncResult.Error(StringHolder.Formatted(R.string.error_connection, listOf()))
                } catch (e: SerializationException) {
                    Timber.tag(TAG).e(e, "JSON parse error during sync")
                    val err = DomainError.Local("Failed to parse prompt JSON")
                    _status.value = SyncStatus.Error(err)
                    SyncResult.Error(StringHolder.Formatted(R.string.parsing_error, listOf()))
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Unexpected error during sync")
                    val err = DomainError.Generic(e.message ?: R.string.unknown_error.toString())
                    _status.value = SyncStatus.Error(err)
                    SyncResult.Error(StringHolder.Formatted(R.string.unknown_error, listOf()))
                } finally {
                    // Сброс к idle только если не в середине retry
                    if (_status.value !is SyncStatus.InProgress) {
                        _status.value = SyncStatus.None
                    }
                }
            }
        }

    private suspend fun downloadAndProcessArchive(): List<Prompt> =
        withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("Downloading archive from $ARCHIVE_URL")
            val response = service.downloadFile(ARCHIVE_URL)

            if (!response.isSuccessful) {
                throw IOException("Failed to download archive. Code: ${response.code()}, Message: ${response.message()}")
            }

            val body = response.body()
                ?: throw IOException("Archive response body is null")

            // Временная директория для извлечения
            val tempDir = File(context.cacheDir, "temp_prompts_${System.currentTimeMillis()}")

            try {
                // 1️⃣ Распаковываем ZIP
                ZipUtils.extractZip(body.byteStream(), tempDir)
                    .getOrThrow()

                Timber.d("Archive extracted to: ${tempDir.absolutePath}")

                // 2️⃣ Читаем все JSON‑файлы
                val jsonFiles = ZipUtils.readJsonFilesFromDirectory(tempDir)

                Timber.d("Found ${jsonFiles.size} JSON files")

                // 3️⃣ Десериализуем каждый файл
                val prompts = mutableListOf<Prompt>()
                for ((category, content) in jsonFiles) {
                    try {
                        val promptJson = json.decodeFromString<PromptJson>(content)
                        if (promptJson.category.isNullOrBlank()) {
                            promptJson.category = category
                        }
                        prompts.add(promptJson.toDomain())
                    } catch (e: Exception) {
                        Timber.w("Failed to parse JSON from $category: ${e.message}")
                    }
                }

                prompts
            } finally {
                tempDir.deleteRecursively()
                Timber.d("Temporary directory cleaned up")
            }
        }

    private suspend fun handleDeletedPrompts(prompts: List<Prompt>): List<String> {
        val localPrompts = promptsRepository.observeAllPrompts().first()

        val remoteIds = prompts.map { it.id }.toSet()

        // Удаляем локальные подсказки, которых больше нет на удалённом сервере
        val idsToDelete = localPrompts
            .filter { !it.isLocal && it.id !in remoteIds }
            .map { it.id }

        if (idsToDelete.isNotEmpty()) {
            Timber.i("Deleting ${idsToDelete.size} prompts that are no longer on remote.")
            promptsRepository.deletePromptsByIds(idsToDelete)
        }
        return idsToDelete
    }

    override suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        prefs.get<Long>(LAST_SYNC_KEY) ?: 0L
    }

    override suspend fun setLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.put(LAST_SYNC_KEY, timestamp)
    }
}
