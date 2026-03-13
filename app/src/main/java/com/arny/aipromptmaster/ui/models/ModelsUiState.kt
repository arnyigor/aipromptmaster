package com.arny.aipromptmaster.ui.models

import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter

data class ModelsUiState(
    val models: List<LlmModel> = emptyList(),
    val isLoading: Boolean = false,
    val filter: ModelsFilter = ModelsFilter(),
    val error: String? = null
)
