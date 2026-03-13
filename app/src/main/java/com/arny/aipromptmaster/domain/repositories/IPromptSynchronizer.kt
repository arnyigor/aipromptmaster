package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface IPromptSynchronizer {
    val status: StateFlow<SyncStatus>
    suspend fun synchronize(): SyncResult
    suspend fun getLastSyncTime(): Long
    suspend fun setLastSyncTime(timestamp: Long)
}