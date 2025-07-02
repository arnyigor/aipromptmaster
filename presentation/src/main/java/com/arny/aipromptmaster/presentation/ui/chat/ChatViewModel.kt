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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUIState>(ChatUIState.Initial)
    val uiState: StateFlow<ChatUIState> = _uiState.asStateFlow()

    private val messages = mutableListOf<String>()

    private val _selectedModel = MutableStateFlow<DataResult<LLMModel>>(DataResult.Loading)
    val selectedModelResult: StateFlow<DataResult<LLMModel>> = _selectedModel.asStateFlow()

    init {
        loadSelectedModel()
    }

    private fun loadSelectedModel() {
        viewModelScope.launch {
            interactor.getSelectedModel()
                .catch { e ->
                    _selectedModel.value = DataResult.Error(e)
                }
                .collect { result ->
                    _selectedModel.value = result
                }
        }
    }


    // Внутри ChatViewModel
    fun sendMessage(message: String) {
        // 3. Получаем текущее состояние модели из StateFlow
        val modelResult = _selectedModel.value

        // 4. Проверяем, что модель успешно загружена
        if (modelResult is DataResult.Success) {
            val modelId = modelResult.data.id // Получаем ID модели

            viewModelScope.launch {
                interactor.sendMessage(modelId, message)
                    .onStart {
                        updateState(isLoading = true)
                    }
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
                                // Этот блок может быть не нужен, если вы используете onStart
                            }
                        }
                    }
                    .catch { e ->
                        updateState(error = e)
                    }
                    .onCompletion {
                        // Скрываем индикатор загрузки после завершения потока
                        updateState(isLoading = false)
                    }
                    .collect() // Запускаем сбор данных
            }
        } else {
            // Обработка случая, когда модель не выбрана или произошла ошибка загрузки
            updateState(error = IllegalStateException("Модель не выбрана или не удалось её загрузить."))
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