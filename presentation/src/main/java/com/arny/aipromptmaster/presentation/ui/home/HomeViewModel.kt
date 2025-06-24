package com.arny.aipromptmaster.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.results.LLMResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LLMUIState>(LLMUIState.Initial)
    val uiState: StateFlow<LLMUIState> = _uiState.asStateFlow()

    private val messages = mutableListOf<String>()

    private val _modelsState = MutableStateFlow<LLMResult<List<LLMModel>>>(LLMResult.Loading())
    val modelsState: StateFlow<LLMResult<List<LLMModel>>> = _modelsState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            interactor.getModels()
                .collect { result ->
                    _modelsState.value = result
                }
        }
    }

    fun sendMessage(model: String, message: String) {
        viewModelScope.launch {
            interactor.sendMessage(model, message)
                .onEach { result ->
                    when (result) {
                        is LLMResult.Success -> {
                            messages.add(result.data)
                            updateState()
                        }

                        is LLMResult.Error -> {
                            updateState(error = result.exception)
                        }

                        is LLMResult.Loading -> {
                            updateState(isLoading = result.isLoading)
                        }
                    }
                }
                .catch { e ->
                    updateState(error = e)
                }
                .collect()
        }
    }

    private fun updateState(
        isLoading: Boolean = false,
        error: Throwable? = null
    ) {
        _uiState.value = LLMUIState.Content(
            messages = messages.toList(),
            isLoading = isLoading,
            error = error
        )
    }
}