package com.arny.aipromptmaster.presentation.ui.view

import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString

sealed class PromptViewUiEvent {
    data class ShowError(val message: IWrappedString?) : PromptViewUiEvent()
    data class PromptUpdated(val id: String?) : PromptViewUiEvent()
}