package com.arny.aipromptmaster.presentation.ui.addprompt

import com.arny.aipromptmaster.domain.models.strings.StringHolder

sealed class AddPromptUiState {
    data object Loading : AddPromptUiState()
    data object Content : AddPromptUiState()
    data class Error(val error: StringHolder) : AddPromptUiState()
}