package com.arny.aiprompts.presentation.features.llm

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Реализация компонента
class DefaultLlmComponent(
    componentContext: ComponentContext,
    private val llmInteractor: ILLMInteractor
) : LlmComponent, ComponentContext by componentContext {

    // CoroutineScope, привязанный к жизненному циклу компонента (аналог viewModelScope)
    // Эта функция-расширение создает scope, который автоматически
    // отменится при уничтожении компонента (onDestroy).
    private val scope = coroutineScope()

    private val _uiState = MutableStateFlow(LlmUiState())
    override val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    init {
        // При уничтожении компонента scope будет отменен
        lifecycle.doOnDestroy {
            scope.cancel()
        }

        // Вся логика из init ViewModel переезжает сюда
        llmInteractor.getModels()
            .onEach { modelsResult ->
                _uiState.update { it.copy(modelsResult = modelsResult) }
            }
            .launchIn(scope)

        refreshModels()
    }

    override fun onPromptChanged(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    override fun onModelSelected(modelId: String) {
        scope.launch {
            llmInteractor.toggleModelSelection(modelId)
        }
    }

    override fun onGenerateClicked() {
        val currentState = _uiState.value
        val selectedModel = currentState.selectedModel ?: return
        if (currentState.isGenerating) return

        scope.launch {
            llmInteractor.sendMessage(selectedModel.id, currentState.prompt)
                .onStart {
                    _uiState.update { it.copy(isGenerating = true, responseText = "") }
                }
                .onCompletion {
                    _uiState.update { it.copy(isGenerating = false) }
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> _uiState.update { it.copy(responseText = result.data) }
                        is DataResult.Error -> _uiState.update { it.copy(responseText = "Error: ${result.exception?.message}") }
                        is DataResult.Loading -> _uiState.update { it.copy(responseText = "Generating...") }
                    }
                }
        }
    }

    override fun refreshModels() {
        scope.launch {
            llmInteractor.refreshModels()
        }
    }
}