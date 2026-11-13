package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface IPromptSynchronizer {
    val status: StateFlow<SyncStatus>

    /** Текущий этап синхронизации (для индикатора прогресса). */
    val stage: StateFlow<com.arny.aipromptmaster.domain.models.SyncStage>

    /** Прогресс текущего этапа 0..1 (0, если этап не измеряется). */
    val progress: StateFlow<Float>
    suspend fun synchronize(): SyncResult
    suspend fun getLastSyncTime(): Long
    suspend fun setLastSyncTime(timestamp: Long)
}