package com.arny.aiprompts.presentation.features.llm

import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.results.DataResult

// Единый класс состояния для всего экрана
data class LlmUiState(
    val modelsResult: DataResult<List<LlmModel>> = DataResult.Loading,
    val filteredModels: List<LlmModel> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ModelCategory = ModelCategory.ALL,
    val selectedSortOrder: ModelSortOrder = ModelSortOrder.NAME,
    val prompt: String = "",
    val chatHistory: List<ChatMessage> = emptyList(),
    val showModelSearch: Boolean = false,
    val errorMessage: String? = null
) {
    // Вспомогательное свойство для удобного доступа к выбранной модели
    val selectedModel: LlmModel?
        get() = when (modelsResult) {
            is DataResult.Success -> modelsResult.data.firstOrNull { it.isSelected }
            else -> null
        }

    // Проверяем, есть ли streaming сообщения в истории
    val isGenerating: Boolean
        get() = chatHistory.any { msg -> msg.isStreaming() }

    // Отфильтрованные и отсортированные модели для отображения
    val displayModels: List<LlmModel>
        get() = when (modelsResult) {
            is DataResult.Success -> {
                var filtered = modelsResult.data

                // Применяем поиск
                if (searchQuery.isNotBlank()) {
                    filtered = filtered.filter { it.name.contains(searchQuery, ignoreCase = true) ||
                                                it.description.contains(searchQuery, ignoreCase = true) }
                }

                // Применяем фильтр категории
                filtered = when (selectedCategory) {
                    ModelCategory.ALL -> filtered
                    ModelCategory.FREE -> filtered.filter {
                        it.pricingPrompt == null || it.pricingPrompt.toDouble() == 0.0
                    }
                    ModelCategory.TEXT -> filtered.filter { "text" in it.inputModalities }
                    ModelCategory.VISION -> filtered.filter { "image" in it.inputModalities }
                    ModelCategory.CODE -> filtered.filter {
                        "text" in it.inputModalities && "code" in it.outputModalities
                    }
                }

                // Применяем сортировку
                filtered = when (selectedSortOrder) {
                    ModelSortOrder.NAME -> filtered.sortedBy { it.name }
                    ModelSortOrder.CONTEXT_LENGTH -> filtered.sortedByDescending { it.contextLength }
                    ModelSortOrder.CREATED -> filtered.sortedByDescending { it.created }
                }

                filtered
            }
            else -> emptyList()
        }
}

enum class ModelCategory(val displayName: String) {
    ALL("Все модели"),
    FREE("Бесплатные"),
    TEXT("Только текст"),
    VISION("С изображениями"),
    CODE("Для кода")
}

enum class ModelSortOrder(val displayName: String) {
    NAME("По имени"),
    CONTEXT_LENGTH("По контексту"),
    CREATED("По дате создания")
}