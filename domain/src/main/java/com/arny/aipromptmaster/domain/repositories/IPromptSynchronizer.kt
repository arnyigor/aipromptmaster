package com.arny.aipromptmaster.domain.repositories

interface IPromptSynchronizer {
    suspend fun synchronize(): SyncResult
    suspend fun getLastSyncTime(): Long
    suspend fun setLastSyncTime(timestamp: Long)
} 