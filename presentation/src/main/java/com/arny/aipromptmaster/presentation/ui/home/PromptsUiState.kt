package com.arny.aipromptmaster.presentation.ui.home

sealed class PromptsUiState {
    data object Initial : PromptsUiState()
    data object Loading : PromptsUiState()
    data object Content : PromptsUiState()
    data object Empty : PromptsUiState()
    data class Error(val error: Throwable) : PromptsUiState()
}


