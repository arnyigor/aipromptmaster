package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
) : ViewModel() {
    private val _sendingState = MutableStateFlow(SendingState())

    // ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ ДЛЯ UI
    val uiState: StateFlow<ChatUiState> = combine(
        interactor.getChatHistoryFlow(),    // Источник №1: История сообщений
        interactor.getSelectedModel(),      // Источник №2: Выбранная модель
        _sendingState                       // Источник №3: Состояние отправки/ошибки
    ) { messages, modelResult, sendingState ->
        val selectedModel = (modelResult as? DataResult.Success)?.data
        val modelError = (modelResult as? DataResult.Error)?.exception
        ChatUiState(
            messages = messages,
            selectedModel = selectedModel,
            isLoading = sendingState.isLoading,
            error = sendingState.error ?: modelError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatUiState() // Начальное пустое состояние
    )

    private val modelsResult: StateFlow<DataResult<List<LlmModel>>> = interactor.getModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )

    val selectedModelResult: StateFlow<DataResult<LlmModel>> = interactor.getSelectedModel()
        .catch { throwable ->
            emit(DataResult.Error(throwable))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )

    init {
        refreshModels()
    }

    private fun refreshModels() {
        viewModelScope.launch {
            val initialData = modelsResult.value
            if (initialData is DataResult.Loading ||
                (initialData is DataResult.Success && initialData.data.isEmpty())
            ) {
                interactor.refreshModels()
            }
        }
    }

    // В ChatViewModel.kt
    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) return

        val selectedModelId = uiState.value.selectedModel?.id
        if (selectedModelId == null) {
            _sendingState.value = SendingState(error = IllegalStateException("Модель не выбрана"))
            return
        }

        viewModelScope.launch {
            interactor.sendMessage(selectedModelId, userMessageText)
                .collect { result ->
                    when (result) {
                        is DataResult.Loading -> _sendingState.value =
                            SendingState(isLoading = true)

                        is DataResult.Success -> _sendingState.value =
                            SendingState(isLoading = false)

                        is DataResult.Error -> _sendingState.value =
                            SendingState(isLoading = false, error = result.exception)
                    }
                }
        }
    }

    fun errorShown() {
        _sendingState.update { it.copy(error = null) }
    }
}