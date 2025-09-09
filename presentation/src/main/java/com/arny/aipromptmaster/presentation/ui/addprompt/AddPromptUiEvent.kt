package com.arny.aipromptmaster.presentation.ui.addprompt

sealed class AddPromptUiEvent {
    data object PromptSaved : AddPromptUiEvent()
    data class ValidationError(val field: ValidationField, val message: String) : AddPromptUiEvent()
}

enum class ValidationField {
    TITLE,
    CATEGORY,
    CONTENT_RU,
    CONTENT_EN
}