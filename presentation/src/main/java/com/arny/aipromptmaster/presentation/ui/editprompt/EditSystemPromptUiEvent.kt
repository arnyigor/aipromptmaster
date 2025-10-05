package com.arny.aipromptmaster.presentation.ui.editprompt

sealed class EditSystemPromptUiEvent {
    data object PromptSaved : EditSystemPromptUiEvent()
    data class ValidationError(val message: String) : EditSystemPromptUiEvent()
}