package com.arny.aipromptmaster.presentation.ui.home

import com.arny.aipromptmaster.domain.models.SyncConflict

sealed class PromptsUiState {
    data object Initial : PromptsUiState()
    data object Loading : PromptsUiState()
    data object Content : PromptsUiState()
    data object Empty : PromptsUiState()
    data class Error(val error: Throwable) : PromptsUiState()

    // Sync states
    data object SyncInProgress : PromptsUiState()
    data object SyncError : PromptsUiState()
    data class SyncSuccess(val updatedCount: Int) : PromptsUiState()
    data class SyncConflicts(val conflicts: List<SyncConflict>) : PromptsUiState()
}