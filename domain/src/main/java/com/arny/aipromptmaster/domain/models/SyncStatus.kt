package com.arny.aipromptmaster.domain.models

sealed class SyncStatus {
    object None : SyncStatus()
    object InProgress : SyncStatus()
    object Error : SyncStatus()
    data class Success(val updatedCount: Int) : SyncStatus()
    data class Conflicts(val conflicts: List<SyncConflict>) : SyncStatus()
}