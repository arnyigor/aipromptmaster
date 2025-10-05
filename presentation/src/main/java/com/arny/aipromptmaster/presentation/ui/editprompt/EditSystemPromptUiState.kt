package com.arny.aipromptmaster.presentation.ui.editprompt

sealed class EditSystemPromptUiState {
    data object Loading : EditSystemPromptUiState()
    data object Content : EditSystemPromptUiState()
    data class Error(val message: String) : EditSystemPromptUiState()
}