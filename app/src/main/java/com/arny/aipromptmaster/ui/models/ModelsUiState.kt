package com.arny.aipromptmaster.ui.models

import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter

data class ModelsUiState(
    val models: List<LlmModel> = emptyList(),
    val isLoading: Boolean = false,
    val filter: ModelsFilter = ModelsFilter(),
    val error: String? = null,
    // Состояние проверки доступности моделей
    val isCheckingAvailability: Boolean = false,
    val checkingProgress: Float = 0f,
    val checkedCount: Int = 0,
    val totalToCheck: Int = 0,
    // Статистика после проверки
    val availableCount: Int = 0,
    val totalCheckedCount: Int = 0,
    val bestRatedModel: LlmModel? = null
)
