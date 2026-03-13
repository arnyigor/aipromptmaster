package com.arny.aipromptmaster.domain.models

import com.arny.aipromptmaster.domain.models.errors.DomainError

sealed class SyncStatus {
    data object None : SyncStatus()
    data object InProgress : SyncStatus()
    data object Cooldown : SyncStatus()
    data class Error(val error: DomainError) : SyncStatus()
    data class Success(val updatedCount: Int) : SyncStatus()
}