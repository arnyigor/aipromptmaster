package com.arny.aipromptmaster.presentation.ui.modelsview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.ui.models.FilterState
import com.arny.aipromptmaster.presentation.ui.models.ModalityType
import com.arny.aipromptmaster.presentation.ui.models.SortCriteria
import com.arny.aipromptmaster.presentation.ui.models.SortDirection
import com.arny.aipromptmaster.presentation.ui.models.SortType
import dagger.assisted.AssistedInject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ModelsViewModel @AssistedInject constructor(
    private val llmInteractor: ILLMInteractor,
) : ViewModel() {

    // Приватные StateFlow для управления состоянием
    private val searchQuery = MutableStateFlow("")
    private val filters = MutableStateFlow(FilterState())

    /**
     * Основной поток UI состояния с комбинированием данных моделей, поискового запроса и фильтров
     */
    @OptIn(FlowPreview::class)
    val uiState: StateFlow<DataResult<List<LlmModel>>> =
        combine(
            llmInteractor.getModels(),
            searchQuery.debounce(300L),
            filters
        ) { dataResult, query, filterState ->
            when (dataResult) {
                is DataResult.Success -> {
                    val processedModels = processModels(dataResult.data, query, filterState)
                    DataResult.Success(processedModels)
                }

                is DataResult.Loading, is DataResult.Error -> dataResult
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )

    /**
     * Переключает выбор модели
     */
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            llmInteractor.toggleModelSelection(modelId)
        }
    }

    /**
     * Обновляет поисковый запрос
     */
    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    /**
     * Применяет новые фильтры
     */
    fun applyFilters(newFilters: FilterState) {
        // Обновляем поисковый запрос отдельно для работы debounce
        searchQuery.value = newFilters.searchQuery
        // Обновляем фильтры, исключив поисковый запрос (он обрабатывается отдельно)
        filters.value = newFilters.copy(searchQuery = "")
    }

    /**
     * Основная логика обработки моделей: фильтрация и сортировка
     */
    private fun processModels(
        models: List<LlmModel>,
        query: String,
        filterState: FilterState
    ): List<LlmModel> = models
        .let { applySearchFilter(it, query) }
        .let { applySelectionFilter(it, filterState) }
        .let { applyFavoriteFilter(it, filterState) }
        .let { applyPriceFilter(it, filterState) }
        .let { applyModalityFilter(it, filterState) }
        .let { applySorting(it, filterState.sortOptions) }

    /**
     * Применяет поисковый фильтр по названию, ID и описанию модели
     */
    private fun applySearchFilter(models: List<LlmModel>, query: String): List<LlmModel> {
        if (query.isBlank()) return models

        val lowercaseQuery = query.lowercase()
        return models.filter { model ->
            model.name.lowercase().contains(lowercaseQuery) ||
                    model.id.lowercase().contains(lowercaseQuery) ||
                    model.description.lowercase().contains(lowercaseQuery)
        }
    }

    /**
     * Применяет фильтр по выбранным моделям
     */
    private fun applySelectionFilter(
        models: List<LlmModel>,
        filterState: FilterState
    ): List<LlmModel> = if (filterState.showOnlySelected) {
        models.filter { it.isSelected }
    } else {
        models
    }

    /**
     * Применяет фильтр по избранным моделям
     */
    private fun applyFavoriteFilter(
        models: List<LlmModel>,
        filterState: FilterState
    ): List<LlmModel> = if (filterState.showOnlyFavorites) {
        models.filter { it.isFavorite }
    } else {
        models
    }

    /**
     * Применяет ценовые фильтры
     */
    private fun applyPriceFilter(models: List<LlmModel>, filterState: FilterState): List<LlmModel> {
        var filteredModels = models

        // Фильтр только бесплатных моделей
        if (filterState.showOnlyFree) {
            filteredModels = filteredModels.filter { model ->
                isModelFree(model)
            }
        }

        return filteredModels
    }

    /**
     * Применяет фильтр по модальностям (возможностям модели)
     */
    private fun applyModalityFilter(
        models: List<LlmModel>,
        filterState: FilterState
    ): List<LlmModel> {
        if (filterState.requiredModalities.isEmpty()) return models

        return models.filter { model ->
            filterState.requiredModalities.all { requiredModality ->
                modelSupportsModality(model, requiredModality)
            }
        }
    }

    /**
     * Применяет множественную сортировку по приоритету
     */
    private fun applySorting(
        models: List<LlmModel>,
        sortOptions: List<SortCriteria>
    ): List<LlmModel> {
        if (sortOptions.isEmpty()) return models

        return models.sortedWith { model1, model2 ->
            // Проходим по каждому критерию сортировки по приоритету
            for (criteria in sortOptions) {
                val comparison = compareModelsByCriteria(model1, model2, criteria.type)

                // Применяем направление сортировки
                val adjustedComparison = if (criteria.direction == SortDirection.DESC) {
                    -comparison
                } else {
                    comparison
                }

                // Если модели не равны по этому критерию, возвращаем результат
                if (adjustedComparison != 0) {
                    return@sortedWith adjustedComparison
                }
            }
            // Если все критерии дали равенство, модели считаются равными
            0
        }
    }

    /**
     * Сравнивает две модели по конкретному критерию
     */
    private fun compareModelsByCriteria(
        model1: LlmModel,
        model2: LlmModel,
        sortType: SortType
    ): Int = when (sortType) {
        SortType.NONE -> 0
        SortType.BY_NAME -> model1.name.compareTo(model2.name, ignoreCase = true)
        SortType.BY_PRICE -> {
            val avgPrice1 = calculateAveragePrice(model1)
            val avgPrice2 = calculateAveragePrice(model2)
            avgPrice1.compareTo(avgPrice2)
        }

        SortType.BY_DATE -> model1.created.compareTo(model2.created)
        SortType.BY_CONTEXT -> model1.contextLength.compareTo(model2.contextLength)
    }

    /**
     * Вычисляет среднюю цену модели (prompt + completion) / 2
     */
    private fun calculateAveragePrice(model: LlmModel): BigDecimal {
        return (model.pricingPrompt + model.pricingCompletion).divide(BigDecimal(2))
    }

    /**
     * Проверяет, является ли модель бесплатной
     */
    private fun isModelFree(model: LlmModel): Boolean = model.pricingPrompt == BigDecimal.ZERO &&
            model.pricingCompletion == BigDecimal.ZERO &&
            (model.pricingImage == null || model.pricingImage == BigDecimal.ZERO)

    /**
     * Проверяет, поддерживает ли модель определенную модальность
     */
    private fun modelSupportsModality(model: LlmModel, modalityType: ModalityType): Boolean {
        val supportedInputModalities = model.inputModalities.mapNotNull {
            ModalityType.fromApiString(it)
        }
        val supportedOutputModalities = model.outputModalities.mapNotNull {
            ModalityType.fromApiString(it)
        }

        return modalityType in supportedInputModalities ||
                modalityType in supportedOutputModalities
    }

    /**
     * Переключает избранное для модели
     */
    fun toggleFavorite(modelId: String) {
        viewModelScope.launch {
            llmInteractor.toggleModelFavorite(modelId)
        }
    }
}
