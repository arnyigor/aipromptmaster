package com.arny.aiprompts.presentation.ui.detail

import com.arny.aiprompts.domain.model.Prompt


data class PromptDetailState(
    val prompt: Prompt? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false,
    val draftPrompt: Prompt? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val availableTags: List<String> = emptyList()
)

enum class PromptLanguage {
    RU, EN
}