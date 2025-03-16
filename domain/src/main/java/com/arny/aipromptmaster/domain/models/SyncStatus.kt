package com.arny.aipromptmaster.domain.models

sealed class SyncStatus {
    data object None : SyncStatus()
    data object InProgress : SyncStatus()
    data object Error : SyncStatus()
    data class Success(val updatedCount: Int) : SyncStatus()
    data class Conflicts(val conflicts: List<SyncConflict>) : SyncStatus()
}