package com.arny.aipromptmaster.presentation.ui.addprompt

import com.arny.aipromptmaster.domain.models.strings.StringHolder

sealed class EditPromptUiState {
    data object Loading : EditPromptUiState()
    data object Content : EditPromptUiState()
    data class Error(val error: StringHolder) : EditPromptUiState()
}