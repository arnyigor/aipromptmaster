package com.arny.aiprompts.domain.repositories

interface IPromptSynchronizer {
    suspend fun synchronize(ignoreCooldown: Boolean = false): SyncResult
    suspend fun loadLocalPrompts(): SyncResult
    suspend fun getLastSyncTime(): Long
    suspend fun setLastSyncTime(timestamp: Long)
}