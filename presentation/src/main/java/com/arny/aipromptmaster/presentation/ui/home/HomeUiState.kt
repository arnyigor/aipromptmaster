package com.arny.aipromptmaster.presentation.ui.home

import com.arny.aipromptmaster.domain.models.SyncConflict

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    object Content : HomeUiState()
    object Empty : HomeUiState()
    data class Error(val error: Throwable) : HomeUiState()

    // Sync states
    object SyncInProgress : HomeUiState()
    object SyncError : HomeUiState()
    data class SyncSuccess(val updatedCount: Int) : HomeUiState()
    data class SyncConflicts(val conflicts: List<SyncConflict>) : HomeUiState()
}