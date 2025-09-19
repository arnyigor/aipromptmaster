package com.arny.aiprompts.presentation.features.llm

import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult

// Единый класс состояния для всего экрана
data class LlmUiState(
    val modelsResult: DataResult<List<LlmModel>> = DataResult.Loading,
    val prompt: String = "Why is the sky blue? Write a short answer.",
    val responseText: String = "Response will appear here...",
    val isGenerating: Boolean = false
) {
    // Вспомогательное свойство для удобного доступа к выбранной модели
    val selectedModel: LlmModel?
        get() = when (modelsResult) {
            is DataResult.Success -> modelsResult.data.firstOrNull { it.isSelected }
            else -> null
        }
}