package com.arny.aipromptmaster.presentation.ui.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.models.strings.toErrorHolder
import com.arny.aipromptmaster.presentation.R
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
                    _uiState.value = PromptViewUiState.Content(
                        prompt = prompt,
                        selectedVariantIndex = -1,
                        availableVariants = prompt.promptVariants,
                        currentContent = prompt.content,
                        isLocal = prompt.isLocal
                    )
                } else {
                    _uiState.value =
                        PromptViewUiState.Error(StringHolder.Resource(R.string.prompt_not_found))
                }
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    fun selectVariant(variantIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is PromptViewUiState.Content) {
                val newContent = if (variantIndex == -1) {
                    // Выбран основной контент
                    currentState.prompt.content
                } else {
                    // Выбран вариант
                    currentState.availableVariants.getOrNull(variantIndex)?.content
                        ?: currentState.prompt.content
                }

                _uiState.value = currentState.copy(
                    selectedVariantIndex = variantIndex,
                    currentContent = newContent
                )

                _uiEvent.emit(PromptViewUiEvent.VariantSelected(variantIndex))
            }
        }
    }

    fun copyContent(content: String, label: String) {
        viewModelScope.launch {
            _uiEvent.emit(PromptViewUiEvent.CopyContent(content, label))
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is PromptViewUiState.Content) {
                    val promptId = currentState.prompt.id
                    interactor.toggleFavorite(promptId)
                    _uiState.value = currentState.copy(
                        prompt = currentState.prompt.copy(
                            isFavorite = !currentState.prompt.isFavorite
                        )
                    )
                    _uiEvent.emit(PromptViewUiEvent.PromptUpdated(promptId))
                }
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    fun showDeleteConfirmation() {
        viewModelScope.launch {
            _uiEvent.emit(PromptViewUiEvent.ShowDeleteConfirmation)
        }
    }

    fun deletePrompt() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is PromptViewUiState.Content && currentState.isLocal) {
                    interactor.deletePrompt(promptId)
                    _uiEvent.emit(PromptViewUiEvent.PromptDeleted(promptId))
                    _uiEvent.emit(PromptViewUiEvent.NavigateBack)
                }
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun showError(e: Exception) {
        _uiState.value = PromptViewUiState.Error(e.toErrorHolder())
    }
}