package com.arny.aipromptmaster.presentation.ui.view

import com.arny.aipromptmaster.domain.models.Prompt

sealed class PromptViewUiState {
    data object Initial : PromptViewUiState()
    data object Loading : PromptViewUiState()
    data class Content(val prompt: Prompt) : PromptViewUiState()
    data class Error(val message: String) : PromptViewUiState()
} 