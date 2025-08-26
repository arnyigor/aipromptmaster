package com.arny.aipromptmaster.presentation.ui.view

import com.arny.aipromptmaster.domain.models.strings.StringHolder

sealed class PromptViewUiEvent {
    data class ShowError(val stringHolder: StringHolder?) : PromptViewUiEvent()
    data class PromptUpdated(val id: String?) : PromptViewUiEvent()
    data class VariantSelected(val variantIndex: Int) : PromptViewUiEvent()
    data class CopyContent(val content: String, val label: String) : PromptViewUiEvent()
}