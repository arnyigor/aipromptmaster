package com.arny.aiprompts.presentation.features.llm

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Реализация UI‑компонента для работы с LLM‑моделями.
 *
 * Компонент управляет состоянием экрана генерации текста, подписывается на поток моделей и
 * историю чата из `ILLMInteractor`, а также обрабатывает пользовательские действия
 * (выбор модели, отправка сообщения, отмена/повтор запроса, навигация).
 *
 * @param componentContext Контекст Decompose‑компонента.
 * @param llmInteractor Слой бизнес‑логики для взаимодействия с LLM‑сервисом.
 * @param onBack Функция‑обработчик «назад» (вызывается при переходе к предыдущему экрану).
 */
class DefaultLlmComponent(
    componentContext: ComponentContext,
    private val llmInteractor: ILLMInteractor,
    private val onBack: () -> Unit,
) : LlmComponent, ComponentContext by componentContext {

    /** Корутинный scope, привязанный к жизненному циклу компонента. */
    private val scope = coroutineScope()

    /** Текущая задача генерации (если есть), чтобы можно было отменить её. */
    private var streamingJob: Job? = null

    /** Состояние UI, опубликованное через `StateFlow`. */
    private val _uiState = MutableStateFlow(LlmUiState())
    override val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    init {
        // Подписка на список моделей
        llmInteractor.getModels()
            .onEach { modelsResult ->
                _uiState.update { it.copy(modelsResult = modelsResult) }
            }
            .launchIn(scope)

        // Подписка на историю чата
        llmInteractor.getChatHistoryFlow()
            .onEach { chatHistory ->
                _uiState.update { state ->
                    state.copy(chatHistory = chatHistory)
                }
            }
            .launchIn(scope)

        // Инициируем загрузку моделей при создании компонента
        scope.launch {
            llmInteractor.refreshModels()
        }
    }

    /** Вызывается при изменении текста запроса в поле ввода. */
    override fun onPromptChanged(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    /**
     * Обрабатывает выбор модели пользователем.
     *
     * Внутри вызывает `toggleModelSelection`, чтобы либо выбрать новую модель,
     * либо снять выделение с текущей.
     */
    override fun onModelSelected(modelId: String) {
        scope.launch {
            llmInteractor.toggleModelSelection(modelId)
        }
    }

    /**
     * Запускает потоковую генерацию текста модели.
     *
     * Выполняет валидацию выбранной модели, состояния генерации и пустоты запроса,
     * отменяет предыдущий поток (если он существует) и подписывается на
     * `sendStreamingMessage`. Обновления приходят как `DataResult`‑объекты.
     */
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
                .onCompletion { /* можно обработать завершение потока */ }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            // Обновления идут через chatHistory flow
                            Logger.d("DefaultLlmComponent","Streaming chunk received message:${result.data.content}")
                        }
                        is DataResult.Error -> {
                            val errorMsg = result.exception?.message ?: "Ошибка генерации"
                            _uiState.update { it.copy(errorMessage = errorMsg) }
                        }
                        is DataResult.Loading -> {
                            // Начало стриминга – можно показать индикатор
                        }
                    }
                }
            }
    }

    /** Отменяет текущую потоковую генерацию. */
    override fun onCancelGenerating() {
        streamingJob?.cancel()
        Logger.d("DefaultLlmComponent","Generation cancelled by user")
    }

    /**
     * Запускает повторный запрос для сообщения, которое завершилось ошибкой.
     *
     * Внутри вызывает `retryMessage` в интеракторе и подписывается на результат.
     */
    override fun onRetryMessage(messageId: String) {
        scope.launch {
            llmInteractor.retryMessage(messageId)
        }
    }

    /** Обрабатывает переход «назад» – просто вызываем переданный callback. */
    override fun onNavigateBack() {
        onBack()
    }

    /** Принудительно обновляет список моделей (собирает из API). */
    override fun refreshModels() {
        scope.launch {
            llmInteractor.refreshModels()
        }
    }

    /**
     * Очищает историю чата и сбрасывает поле ввода запроса.
     *
     * После очистки состояние UI обновляется с пустым `prompt`.
     */
    override fun clearChat() {
        scope.launch {
            llmInteractor.clearChat()
            _uiState.update { it.copy(prompt = "") }
        }
    }

    /** Обновляет строку поиска модели. */
    override fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Устанавливает выбранную категорию фильтрации моделей. */
    override fun onCategorySelected(category: ModelCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** Устанавливает порядок сортировки списка моделей. */
    override fun onSortOrderSelected(sortOrder: ModelSortOrder) {
        _uiState.update { it.copy(selectedSortOrder = sortOrder) }
    }


    /** Очищает сообщение об ошибке, если оно было показано. */
    override fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // выбор сессии чата
    override fun onChatSessionSelected(sessionId: String) {
        _uiState.update { it.copy(selectedChatId = sessionId) }
    }

    // создание сессии чата
    override fun onCreateNewChatSession() {
        scope.launch {
            val newSession = ChatSession(
                id = java.util.UUID.randomUUID().toString(),
                name = "Новый чат",
                timestamp = System.currentTimeMillis(),
                lastMessage = "",
                messages = emptyList()
            )
            _uiState.update { state ->
                state.copy(
                    chatSessions = state.chatSessions + newSession,
                    selectedChatId = newSession.id
                )
            }
        }
    }

    // удаление сессии чата
    override fun onDeleteChatSession(sessionId: String) {
        scope.launch {
            _uiState.update { state ->
                val updatedSessions = state.chatSessions.filter { it.id != sessionId }
                val newSelectedId = if (state.selectedChatId == sessionId) {
                    updatedSessions.firstOrNull()?.id
                } else {
                    state.selectedChatId
                }
                state.copy(
                    chatSessions = updatedSessions,
                    selectedChatId = newSelectedId
                )
            }
        }
    }

    // переименование сессии чата
    override fun onRenameChatSession(sessionId: String, newName: String) {
        scope.launch {
            _uiState.update { state ->
                state.copy(
                    chatSessions = state.chatSessions.map {
                        if (it.id == sessionId) it.copy(name = newName) else it
                    }
                )
            }
        }
    }

    // Toggle видимости панелей
    override fun toggleChatList() {
        _uiState.update { it.copy(showChatList = !it.showChatList) }
    }

    override fun toggleParameters() {
        _uiState.update { it.copy(showParameters = !it.showParameters) }
    }

    override fun toggleModelDialog() {
        _uiState.update { it.copy(showModelDialog = !it.showModelDialog) }
    }
}
