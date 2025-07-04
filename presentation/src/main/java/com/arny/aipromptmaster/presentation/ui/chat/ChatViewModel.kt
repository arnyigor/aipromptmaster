package com.arny.aipromptmaster.presentation.ui.chat

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUIState>(ChatUIState.Initial)
    val uiState: StateFlow<ChatUIState> = _uiState.asStateFlow()

    private val messages = mutableListOf<String>()

    private val _modelsState = MutableStateFlow<DataResult<List<LLMModel>>>(DataResult.Loading)
    val modelsState: StateFlow<DataResult<List<LLMModel>>> = _modelsState.asStateFlow()

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
                        is DataResult.Success -> {
                            messages.add(result.data)
                            updateState()
                        }

                        is DataResult.Error -> {
                            updateState(error = result.exception)
                        }

                        is DataResult.Loading -> {
                            updateState(isLoading = true)
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
        _uiState.value = ChatUIState.Content(
            messages = messages.toList(),
            isLoading = isLoading,
            error = error
        )
    }
}