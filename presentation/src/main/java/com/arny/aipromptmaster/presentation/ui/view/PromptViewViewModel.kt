package com.arny.aipromptmaster.presentation.ui.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PromptViewViewModel @AssistedInject constructor(
    @Assisted private val promptId: String,
    private val interactor: IPromptsInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PromptViewUiState>(PromptViewUiState.Initial)
    val uiState: StateFlow<PromptViewUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<PromptViewUiEvent>()
    val uiEvent: SharedFlow<PromptViewUiEvent> = _uiEvent.asSharedFlow()

    fun loadPrompt() {
        viewModelScope.launch {
            _uiState.value = PromptViewUiState.Loading
            try {
                val prompt = interactor.getPromptById(promptId)
                if (prompt != null) {
                    _uiState.value = PromptViewUiState.Content(prompt)
                } else {
                    _uiState.value = PromptViewUiState.Error("Промпт не найден")
                }
            } catch (e: Exception) {
                _uiState.value = PromptViewUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is PromptViewUiState.Content) {
                    val promptId = currentState.prompt.id
                    interactor.toggleFavorite(promptId)
                    _uiState.value = PromptViewUiState.Content(
                        currentState.prompt.copy(
                            isFavorite = !currentState.prompt.isFavorite
                        )
                    )
                    _uiEvent.emit(PromptViewUiEvent.PromptUpdated(promptId))
                }
            } catch (e: Exception) {
                _uiState.value = PromptViewUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
}