package com.arny.aipromptmaster.presentation.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ModelsViewModel @AssistedInject constructor(
    private val llmInteractor: ILLMInteractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DataResult<List<LLMModel>>>(DataResult.Loading)
    val uiState: StateFlow<DataResult<List<LLMModel>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = DataResult.Loading
            llmInteractor.getModels()
                .catch {
                    _uiState.value = DataResult.Error(Throwable())
                }
                .collect { result ->
                    _uiState.value = result
                }
        }
    }

    fun selectModel(selectedModel: LLMModel) {
        viewModelScope.launch {
            if (!selectedModel.isSelected) {
                val result = _uiState.value
                if (result is DataResult.Success<List<LLMModel>>) {
                    val currentList = result.data
                    val updatedList = currentList.map { model ->
                        when (model.id) {
                            selectedModel.id -> {
                                llmInteractor.setSelectedModelId(model.id)
                                model.copy(isSelected = true)
                            }

                            else -> model.copy(isSelected = false)
                        }
                    }
                    _uiState.value = DataResult.Success(updatedList)
                }
            }
        }
    }
}