package com.arny.aipromptmaster.presentation.ui.addprompt

sealed class EditPromptUiEvent {
    data object PromptSaved : EditPromptUiEvent()
    data class ValidationError(val field: ValidationField, val message: String) : EditPromptUiEvent()
}

enum class ValidationField {
    TITLE,
    CATEGORY,
    CONTENT_RU,
    CONTENT_EN
}