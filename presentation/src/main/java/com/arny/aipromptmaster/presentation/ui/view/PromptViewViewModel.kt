package com.arny.aipromptmaster.presentation.ui.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PromptViewViewModel @AssistedInject constructor(
    @Assisted private val promptId: String,
    private val promptsInteractor: IPromptsInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PromptViewUiState>(PromptViewUiState.Initial)
    val uiState: StateFlow<PromptViewUiState> = _uiState.asStateFlow()

    fun loadPrompt() {
        viewModelScope.launch {
            _uiState.value = PromptViewUiState.Loading
            try {
                val prompt = promptsInteractor.getPromptById(promptId)
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
                    val updatedPrompt = currentState.prompt.copy(
                        isFavorite = !currentState.prompt.isFavorite
                    )
                    promptsInteractor.updatePrompt(updatedPrompt)
                    _uiState.value = PromptViewUiState.Content(updatedPrompt)
                }
            } catch (e: Exception) {
                _uiState.value = PromptViewUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
}