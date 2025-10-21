package com.arny.aiprompts.presentation.features.llm

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DefaultLlmComponent(
    componentContext: ComponentContext,
    private val llmInteractor: ILLMInteractor,
    private val onBack: () -> Unit,
) : LlmComponent, ComponentContext by componentContext {

    private val scope = coroutineScope()
    private var streamingJob: Job? = null

    private val _uiState = MutableStateFlow(LlmUiState())
    override val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    init {
        // Подписка на модели
        llmInteractor.getModels()
            .onEach { modelsResult ->
                _uiState.update { it.copy(modelsResult = modelsResult) }
            }
            .launchIn(scope)

        // Подписка на историю чата
        llmInteractor.getChatHistoryFlow()
            .onEach { chatHistory ->
                _uiState.update { state ->
                    state.copy(
                        chatHistory = chatHistory,
                    )
                }
            }
            .launchIn(scope)

        // Загружаем модели
        scope.launch {
            llmInteractor.refreshModels()
        }
    }

    override fun onPromptChanged(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    override fun onModelSelected(modelId: String) {
        scope.launch {
            llmInteractor.toggleModelSelection(modelId)
        }
    }

    override fun onStreamingGenerateClicked() {
        val currentState = _uiState.value
        val selectedModel = currentState.selectedModel

        // Валидация
        when {
            selectedModel == null -> {
                _uiState.update { it.copy(errorMessage = "Выберите модель") }
                return
            }
            currentState.isGenerating -> {
                Logger.w("DefaultLlmComponent","Generation already in progress")
                return
            }
            currentState.prompt.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Введите сообщение") }
                return
            }
        }

        val messageToSend = currentState.prompt

        // Отменяем предыдущий запрос (если есть)
        streamingJob?.cancel()

        streamingJob = scope.launch {
            llmInteractor.sendStreamingMessage(
                model = selectedModel.id,
                userMessage = messageToSend
            )
                .catch { error ->
                    Logger.e(error, "Streaming error")
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message ?: "Произошла ошибка",
                        )
                    }
                }
                .onCompletion {
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            // Обновления идут через chatHistory flow
                            Logger.d("DefaultLlmComponent","Streaming chunk received message:${result.data.content}")
                        }
                        is DataResult.Error -> {
                            val errorMsg = result.exception?.message ?: "Ошибка генерации"
                            _uiState.update {
                                it.copy(errorMessage = errorMsg)
                            }
                        }
                        is DataResult.Loading -> {
                            // Начало стриминга
                        }
                    }
                }
        }
    }

    override fun onCancelGenerating() {
        streamingJob?.cancel()
        Logger.d("DefaultLlmComponent","Generation cancelled by user")
    }

    override fun onRetryMessage(messageId: String) {
        scope.launch {
            llmInteractor.retryMessage(messageId)
        }
    }

    override fun onNavigateBack() {
        onBack()
    }

    override fun refreshModels() {
        scope.launch {
            llmInteractor.refreshModels()
        }
    }

    override fun clearChat() {
        scope.launch {
            llmInteractor.clearChat()
            _uiState.update { it.copy(prompt = "") }
        }
    }

    override fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    override fun onCategorySelected(category: ModelCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    override fun onSortOrderSelected(sortOrder: ModelSortOrder) {
        _uiState.update { it.copy(selectedSortOrder = sortOrder) }
    }

    override fun toggleModelSearch() {
        _uiState.update { it.copy(showModelSearch = !it.showModelSearch) }
    }

    override fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
