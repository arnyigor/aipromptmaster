package com.arny.aipromptmaster.presentation.ui.modelsview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelsViewModel @AssistedInject constructor(
    private val llmInteractor: ILLMInteractor,
) : ViewModel() {

    // 1. StateFlow для хранения поискового запроса. Приватный, чтобы им управлял только ViewModel.
    private val searchQuery = MutableStateFlow("")

    // 2. Объединяем поток моделей и поток поискового запроса
    @OptIn(FlowPreview::class)
    val uiState: StateFlow<DataResult<List<LlmModel>>> =
        combine(
            llmInteractor.getModels(),
            // Опционально, но рекомендуется: debounce для предотвращения слишком частых вычислений
            // при быстром вводе текста.
            searchQuery.debounce(300L)
        ) { dataResult, query ->
            // Эта лямбда будет вызываться каждый раз, когда llmInteractor.getModels()
            // или searchQuery эмитит новое значение.

            when (dataResult) {
                is DataResult.Success -> {
                    val models = dataResult.data
                    if (query.isBlank()) {
                        // Если запрос пустой, возвращаем полный список
                        DataResult.Success(models)
                    } else {
                        // Фильтруем список по запросу (без учета регистра)
                        val filteredList = models.filter { model ->
                            // Здесь вы можете определить логику поиска.
                            // Например, по имени модели.
                            model.name.contains(query, ignoreCase = true) || model.id.contains(query, ignoreCase = true)
                        }
                        DataResult.Success(filteredList)
                    }
                }
                // Для состояний Loading и Error просто пробрасываем их дальше,
                // так как фильтрация к ним не применима.
                is DataResult.Loading, is DataResult.Error -> dataResult
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )


    fun selectModel(clickedModelId: String) {
        viewModelScope.launch {
            llmInteractor.toggleModelSelection(clickedModelId)
        }
    }

    fun filterModels(query: String) {
        searchQuery.value = query
    }
}
