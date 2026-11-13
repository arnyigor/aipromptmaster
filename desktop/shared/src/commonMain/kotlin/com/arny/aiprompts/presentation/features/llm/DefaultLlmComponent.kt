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
    private var messagesJob: Job? = null
    private var observedSessionId: String? = null

    /** Состояние UI. */
    private val restored = stateKeeper.consume("chat-draft", ChatDraft.serializer()) ?: ChatDraft()
    private val drafts = restored.drafts.toMutableMap()
    private val fileDrafts = restored.fileDrafts.toMutableMap()
    private val _uiState = MutableStateFlow(LlmUiState(prompt = restored.input, selectedChatId = restored.chatId,
        attachments = restored.attachments, newChatSystemPrompt = restored.systemPrompt, newChatSettings = restored.settings))
    override val uiState: StateFlow<LlmUiState> = _uiState.asStateFlow()

    /** Триггер для обновления списка моделей. */
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        stateKeeper.register("chat-draft", ChatDraft.serializer()) {
            ChatDraft(_uiState.value.prompt, _uiState.value.selectedChatId, drafts.toMap(),
                _uiState.value.attachments, fileDrafts.toMap(), _uiState.value.newChatSystemPrompt, _uiState.value.newChatSettings)
        }
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
                    val newSelectedId = state.selectedChatId?.takeIf { id -> sessions.any { it.id == id && !it.isArchived } }
                        ?: sessions.firstOrNull { !it.isArchived }?.id
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
    private fun observeCurrentSessionMessages() {
        val selectedId = _uiState.value.selectedChatId
        if (selectedId == observedSessionId && messagesJob?.isActive == true) return
        observedSessionId = selectedId
        // Отменяем предыдущую подписку
        messagesJob?.cancel()
        
        val sessionId = selectedId ?: run {
            _uiState.update { it.copy(messages = emptyList(), isLoadingMessages = false) }
            return
        }
        _uiState.update { it.copy(messages = emptyList(), isLoadingMessages = true) }
        
        messagesJob = llmInteractor.getMessagesForSession(sessionId)
            .onEach { messages ->
                // Гарантируем сортировку по времени (старые -> новые)
                val sortedMessages = messages.sortedBy { it.timestamp }
                _uiState.update { if (it.selectedChatId == sessionId) it.copy(messages = sortedMessages, isLoadingMessages = false) else it }
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
        val sessionId = _uiState.value.selectedChatId
        scope.launch {
            if (sessionId == null) llmInteractor.selectModel(modelId)
            else llmInteractor.selectSessionModel(sessionId, modelId)
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
            llmInteractor.refreshModels()
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
        if (_uiState.value.chatSessions.firstOrNull { it.id == sessionId }?.isArchived == true) {
            _uiState.update { it.copy(errorMessage = "Восстановите чат из архива, чтобы продолжить переписку") }
        } else selectSession(sessionId)
    }

    private fun selectSession(sessionId: String?) {
        val previous = _uiState.value
        previous.selectedChatId?.let { drafts[it] = previous.prompt }
        previous.selectedChatId?.let { fileDrafts[it] = previous.attachments }
        _uiState.update { it.copy(selectedChatId = sessionId,
            prompt = if (sessionId == previous.selectedChatId) previous.prompt else drafts[sessionId].orEmpty(),
            attachments = if (sessionId == previous.selectedChatId) previous.attachments else fileDrafts[sessionId].orEmpty()) }
        observeCurrentSessionMessages()
    }

    override fun onCreateNewChatSession() {
        val defaults = _uiState.value
        scope.launch {
            try {
                val newSession = llmInteractor.createSession(
                    name = "Новый чат",
                    systemPrompt = defaults.newChatSystemPrompt.takeIf { it.isNotBlank() }
                )
                llmInteractor.updateChatSettings(newSession.id, defaults.newChatSettings)
                selectSession(newSession.id)
            } catch (e: Exception) {
                Logger.e(e, "DefaultLlmComponent", "Failed to create session")
                _uiState.update { it.copy(errorMessage = "Не удалось создать чат: ${e.message}") }
            }
        }
    }

    override fun onDeleteChatSession(sessionId: String) { _uiState.update { it.copy(pendingDeleteChatId = sessionId) } }
    override fun onDismissDeleteChatSession() { _uiState.update { it.copy(pendingDeleteChatId = null) } }
    override fun onToggleArchivedChats() { _uiState.update { it.copy(showArchivedChats = !it.showArchivedChats) } }
    override fun onUnarchiveChatSession(sessionId: String) {
        scope.launch {
            try { llmInteractor.unarchiveSession(sessionId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(errorMessage = "Не удалось восстановить чат") } }
        }
    }
    override fun onConfirmDeleteChatSession() {
        val sessionId = _uiState.value.pendingDeleteChatId ?: return
        onDismissDeleteChatSession()
        scope.launch {
            try {
                llmInteractor.deleteSession(sessionId)
                // Если удалили текущую сессию, сбрасываем выбор
                if (_uiState.value.selectedChatId == sessionId) selectSession(_uiState.value.chatSessions.firstOrNull { it.id != sessionId && !it.isArchived }?.id)
                drafts.remove(sessionId)
                fileDrafts.remove(sessionId)
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
        val sessionId = _uiState.value.selectedChatId ?: run {
            _uiState.update { it.copy(newChatSystemPrompt = systemPrompt) }; return
        }
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
        val sessionId = _uiState.value.selectedChatId ?: run {
            _uiState.update { it.copy(newChatSettings = settings) }; return
        }
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
        _uiState.value.selectedChatId?.let { drafts[it] = newPrompt }
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    override fun onAttachmentsAdded(files: List<com.arny.aiprompts.domain.interactors.AttachmentInput>) {
        if (_uiState.value.requestActive) return
        val supported = files.filter { file ->
            val mime = file.mimeType.orEmpty()
            mime.startsWith("text/") || mime.startsWith("image/") || mime in listOf("application/json", "application/xml")
        }
        _uiState.update { it.copy(attachments = (it.attachments + supported).distinctBy { file -> file.uri }.take(8),
            errorMessage = if ((it.attachments + supported).distinctBy { file -> file.uri }.size > 8) "Можно добавить до 8 вложений" else if (supported.size != files.size) "Поддерживаются изображения, текст и исходный код. Другие файлы не добавлены." else it.errorMessage) }
    }
    override fun onAttachmentRemoved(uri: String) { _uiState.update { it.copy(attachments = it.attachments.filterNot { file -> file.uri == uri }) } }
    override fun onAttachmentError(message: String) { _uiState.update { it.copy(errorMessage = message) } }

    override fun onStreamingGenerateClicked() {
        val current = _uiState.value
        if (current.requestActive || (current.prompt.isBlank() && current.attachments.isEmpty())) return
        if (current.selectedModel == null && current.currentSession?.modelId == null) {
            _uiState.update { it.copy(errorMessage = "Выберите модель") }
            return
        }
        val text = current.prompt
        _uiState.update { it.copy(requestActive = true, errorMessage = null) }
        streamingJob = scope.launch {
            var accepted = false
            try {
                val sessionId = current.selectedChatId ?: llmInteractor.createSession(text.take(60).ifBlank { "Новый чат" }, current.newChatSystemPrompt.takeIf { it.isNotBlank() }).id.also {
                    llmInteractor.updateChatSettings(it, current.newChatSettings)
                    selectSession(it)
                    drafts[it] = text
                    fileDrafts[it] = current.attachments
                    _uiState.update { state -> state.copy(prompt = text, attachments = current.attachments) }
                }
                val response = if (current.attachments.isEmpty()) llmInteractor.sendMessage(sessionId, text)
                    else llmInteractor.sendMessageWithAttachments(sessionId, com.arny.aiprompts.domain.interactors.MessageInput(text, current.attachments))
                response.collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            if (!accepted) {
                                accepted = true
                                drafts[sessionId] = ""
                                fileDrafts[sessionId] = emptyList()
                                _uiState.update { if (it.selectedChatId == sessionId) it.copy(prompt = "", attachments = emptyList()) else it }
                            }
                        }
                        is DataResult.Error -> _uiState.update { it.copy(errorMessage = result.exception?.message ?: "Ошибка генерации") }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Ошибка генерации") }
            } finally {
                _uiState.update { it.copy(requestActive = false) }
                streamingJob = null
            }
        }
    }

    override fun onCancelGenerating() {
        streamingJob?.cancel()
        scope.launch {
            llmInteractor.cancelStreaming()
        }
        Logger.d("DefaultLlmComponent", "Generation cancelled")
    }

    override fun onRetryMessage(messageId: String) {
        if (_uiState.value.requestActive) return
        _uiState.update { it.copy(requestActive = true, errorMessage = null) }
        streamingJob = scope.launch {
            try { llmInteractor.retryMessage(messageId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message ?: "Не удалось повторить ответ") } }
            finally { _uiState.update { it.copy(requestActive = false) }; streamingJob = null }
        }
    }

    override fun onEditMessage(messageId: String, newContent: String) {
        onDismissEditMessage()
        if (_uiState.value.requestActive || newContent.isBlank()) return
        _uiState.update { it.copy(requestActive = true, errorMessage = null) }
        streamingJob = scope.launch {
            try {
                llmInteractor.editMessage(messageId, newContent).collect { result ->
                    if (result is DataResult.Error) _uiState.update { it.copy(errorMessage = result.exception?.message ?: "Ошибка редактирования") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(errorMessage = e.message ?: "Ошибка редактирования") } }
            finally { _uiState.update { it.copy(requestActive = false) }; streamingJob = null }
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

    override fun onBeginEditMessage(messageId: String) {
        if (_uiState.value.requestActive) return
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (message.role != com.arny.aiprompts.data.model.ChatMessageRole.USER) return
        _uiState.update { it.copy(editingMessageId = messageId, editingText = message.content) }
    }
    override fun onEditDraftChanged(text: String) { _uiState.update { it.copy(editingText = text) } }
    override fun onDismissEditMessage() { _uiState.update { it.copy(editingMessageId = null, editingText = "") } }

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
    }
}

@kotlinx.serialization.Serializable
private data class ChatDraft(
    val input: String = "", val chatId: String? = null, val drafts: Map<String, String> = emptyMap(),
    val attachments: List<com.arny.aiprompts.domain.interactors.AttachmentInput> = emptyList(),
    val fileDrafts: Map<String, List<com.arny.aiprompts.domain.interactors.AttachmentInput>> = emptyMap(),
    val systemPrompt: String = "", val settings: ChatSettings = ChatSettings(),
)
