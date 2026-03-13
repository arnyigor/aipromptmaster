package com.arny.aiprompts.presentation.features.llm

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Реализация компонента для работы с LLM чатом.
 *
 * ОБНОВЛЕНИЯ (Phase 6):
 * - Полная поддержка сессий чата с persistence в Room
 * - System prompt для каждой сессии
 * - Настройки генерации (temperature, maxTokens, etc.)
 * - Улучшенная обработка ошибок и управление Job
 * - Подписка на изменения сессий и сообщений из БД
 *
 * @param componentContext Контекст Decompose-компонента
 * @param llmInteractor Интерактор для работы с LLM
 * @param onBack Callback для возврата назад
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLlmComponent(
    componentContext: ComponentContext,
    private val llmInteractor: ILLMInteractor,
    private val onBack: () -> Unit,
) : LlmComponent, ComponentContext by componentContext {

    /** Scope для корутин, привязанный к жизненному циклу. */
    private val scope = coroutineScope()

    /** Текущая задача генерации. */
    private var streamingJob: Job? = null

    /** Состояние UI. */
    private val _uiState = MutableStateFlow(LlmUiState())
    override val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    /** Триггер для обновления списка моделей. */
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        setupFlows()
        loadInitialData()
    }

    /** Настраивает потоки данных. */
    private fun setupFlows() {
        // Поток моделей
        refreshTrigger
            .flatMapLatest { llmInteractor.getModels() }
            .onEach { result ->
                _uiState.update { it.copy(modelsResult = result) }
            }
            .launchIn(scope)

        // Поток сессий из БД
        llmInteractor.getAllSessions()
            .onEach { sessions ->
                _uiState.update { state ->
                    // Если нет выбранной сессии, выбираем первую активную
                    val newSelectedId = state.selectedChatId 
                        ?: sessions.firstOrNull()?.id
                    state.copy(
                        chatSessions = sessions,
                        selectedChatId = newSelectedId
                    )
                }
                
                // Обновляем подписку на сообщения текущей сессии
                observeCurrentSessionMessages()
            }
            .launchIn(scope)

        // Начальная загрузка моделей
        scope.launch {
            llmInteractor.refreshModels()
        }
    }

    /** Загружает начальные данные. */
    private fun loadInitialData() {
        scope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    /** Подписка на сообщения текущей сессии. */
    private var messagesJob: Job? = null

    private fun observeCurrentSessionMessages() {
        // Отменяем предыдущую подписку
        messagesJob?.cancel()
        
        val sessionId = _uiState.value.selectedChatId ?: return
        
        messagesJob = llmInteractor.getMessagesForSession(sessionId)
            .onEach { messages ->
                // Гарантируем сортировку по времени (старые -> новые)
                val sortedMessages = messages.sortedBy { it.timestamp }
                _uiState.update { it.copy(messages = sortedMessages) }
            }
            .catch { e ->
                Logger.e(e, "DefaultLlmComponent", "Error loading messages")
            }
            .launchIn(scope)
    }

    // ==================== Навигация ====================

    override fun onNavigateBack() {
        onBack()
    }

    // ==================== Модели ====================

    override fun onModelSelected(modelId: String) {
        scope.launch {
            llmInteractor.toggleModelSelection(modelId)
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

    override fun refreshModels() {
        scope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    override fun toggleModelDialog() {
        val show = !_uiState.value.showModelDialog
        // При открытии диалога всегда обновляем список,
        // чтобы подтянуть модели с текущего Base URL (если он поменялся)
        if (show) {
            refreshModels()
        }
        _uiState.update { it.copy(showModelDialog = show) }
    }

    // ==================== Сессии чата ====================

    override fun onChatSessionSelected(sessionId: String) {
        _uiState.update { it.copy(selectedChatId = sessionId) }
        observeCurrentSessionMessages()
    }

    override fun onCreateNewChatSession() {
        scope.launch {
            try {
                val newSession = llmInteractor.createSession(
                    name = "Новый чат ${System.currentTimeMillis() / 1000}",
                    systemPrompt = null
                )
                _uiState.update { state ->
                    state.copy(selectedChatId = newSession.id)
                }
                observeCurrentSessionMessages()
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to create session")
                _uiState.update { it.copy(errorMessage = "Не удалось создать чат: ${e.message}") }
            }
        }
    }

    override fun onDeleteChatSession(sessionId: String) {
        scope.launch {
            try {
                llmInteractor.deleteSession(sessionId)
                // Если удалили текущую сессию, сбрасываем выбор
                _uiState.update { state ->
                    if (state.selectedChatId == sessionId) {
                        state.copy(selectedChatId = null, messages = emptyList())
                    } else state
                }
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to delete session")
                _uiState.update { it.copy(errorMessage = "Не удалось удалить чат: ${e.message}") }
            }
        }
    }

    override fun onRenameChatSession(sessionId: String, newName: String) {
        scope.launch {
            try {
                llmInteractor.renameSession(sessionId, newName)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to rename session")
                _uiState.update { it.copy(errorMessage = "Не удалось переименовать чат: ${e.message}") }
            }
        }
    }

    override fun onArchiveChatSession(sessionId: String) {
        scope.launch {
            try {
                llmInteractor.archiveSession(sessionId)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to archive session")
                _uiState.update { it.copy(errorMessage = "Не удалось архивировать чат: ${e.message}") }
            }
        }
    }

    override fun onSystemPromptChanged(systemPrompt: String) {
        val sessionId = _uiState.value.selectedChatId ?: return
        scope.launch {
            try {
                llmInteractor.updateSystemPrompt(sessionId, systemPrompt)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to update system prompt")
                _uiState.update { it.copy(errorMessage = "Не удалось обновить system prompt: ${e.message}") }
            }
        }
    }

    override fun onChatSettingsChanged(settings: ChatSettings) {
        val sessionId = _uiState.value.selectedChatId ?: return
        scope.launch {
            try {
                llmInteractor.updateChatSettings(sessionId, settings)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to update settings")
                _uiState.update { it.copy(errorMessage = "Не удалось обновить настройки: ${e.message}") }
            }
        }
    }

    override fun toggleChatList() {
        _uiState.update { it.copy(showChatList = !it.showChatList) }
    }

    // ==================== Сообщения ====================

    override fun onPromptChanged(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    override fun onStreamingGenerateClicked() {
        val currentState = _uiState.value
        val sessionId = currentState.selectedChatId
        val selectedModel = currentState.selectedModel

        // Валидация
        when {
            sessionId == null -> {
                _uiState.update { it.copy(errorMessage = "Выберите или создайте чат") }
                return
            }
            selectedModel == null -> {
                _uiState.update { it.copy(errorMessage = "Выберите модель") }
                return
            }
            currentState.isGenerating -> {
                _uiState.update { 
                    it.copy(errorMessage = "Дождитесь завершения текущей генерации") 
                }
                return
            }
            currentState.prompt.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Введите сообщение") }
                return
            }
        }

        val messageToSend = currentState.prompt

        // Отменяем предыдущий запрос
        streamingJob?.cancel()

        streamingJob = scope.launch {
            // Очищаем prompt сразу после отправки
            _uiState.update { it.copy(prompt = "") }
            
            llmInteractor.sendMessage(
                sessionId = sessionId!!,
                content = messageToSend
            )
                .catch { error ->
                    Logger.e(error, "Streaming error")
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Произошла ошибка")
                    }
                }
                .onCompletion { cause ->
                    streamingJob = null
                    if (cause != null && cause !is CancellationException) {
                        Logger.e(cause, "Streaming completed with error")
                    }
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            // Обновления приходят через Flow сообщений
                            Logger.d("DefaultLlmComponent", "Message updated")
                        }
                        is DataResult.Error -> {
                            val errorMsg = result.exception?.message ?: "Ошибка генерации"
                            _uiState.update { it.copy(errorMessage = errorMsg) }
                        }
                        is DataResult.Loading -> {
                            // Индикатор начала загрузки
                        }
                    }
                }
        }
    }

    override fun onCancelGenerating() {
        streamingJob?.cancel()
        streamingJob = null
        scope.launch {
            llmInteractor.cancelStreaming()
        }
        Logger.d("DefaultLlmComponent", "Generation cancelled")
    }

    override fun onRetryMessage(messageId: String) {
        scope.launch {
            try {
                llmInteractor.retryMessage(messageId)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to retry message")
                _uiState.update { it.copy(errorMessage = "Не удалось повторить: ${e.message}") }
            }
        }
    }

    override fun onEditMessage(messageId: String, newContent: String) {
        scope.launch {
            try {
                llmInteractor.editMessage(messageId, newContent)
                    .collect { result ->
                        when (result) {
                            is DataResult.Success -> {
                                Logger.d("DefaultLlmComponent", "Message edited")
                            }
                            is DataResult.Error -> {
                                _uiState.update { 
                                    it.copy(errorMessage = result.exception?.message ?: "Ошибка редактирования") 
                                }
                            }
                            else -> {}
                        }
                    }
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to edit message")
                _uiState.update { it.copy(errorMessage = "Не удалось отредактировать: ${e.message}") }
            }
        }
    }

    override fun onDeleteMessage(messageId: String) {
        scope.launch {
            try {
                llmInteractor.deleteMessage(messageId)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to delete message")
                _uiState.update { it.copy(errorMessage = "Не удалось удалить сообщение: ${e.message}") }
            }
        }
    }

    override fun clearChat() {
        val sessionId = _uiState.value.selectedChatId
        scope.launch {
            try {
                llmInteractor.clearChat(sessionId)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to clear chat")
                _uiState.update { it.copy(errorMessage = "Не удалось очистить чат: ${e.message}") }
            }
        }
    }

    // ==================== UI ====================

    override fun toggleParameters() {
        _uiState.update { it.copy(showParameters = !it.showParameters) }
    }

    override fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onSearchInHistory(query: String) {
        _uiState.update { 
            it.copy(
                searchHistoryQuery = query,
                isSearchingHistory = query.isNotBlank()
            ) 
        }
        // TODO: Реализовать фильтрацию сообщений по запросу
    }
}
