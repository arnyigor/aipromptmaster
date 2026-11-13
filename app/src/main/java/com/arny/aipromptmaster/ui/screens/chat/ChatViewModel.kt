package com.arny.aipromptmaster.ui.screens.chat

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.models.FileProcessingResult
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import com.arny.aipromptmaster.services.ShareService
import com.arny.aipromptmaster.ui.navigation.AppBarAction
import com.arny.aipromptmaster.ui.navigation.ScreenConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

sealed class ChatUiEvent {
    data class ShowError(val error: Throwable) : ChatUiEvent()
    data object NavigateModels : ChatUiEvent()
    data object ConfirmClearChat : ChatUiEvent()
    data class NavigateToSystem(val conversationId: String) : ChatUiEvent()
    data class ShowMessage(val message: String) : ChatUiEvent()
}

/** Метрики последнего ответа: полное время, символы и скорость генерации. */
@Immutable
data class ResponseMetrics(
    val totalMs: Long,
    val chars: Int,
    val charsPerSecond: Int,
)

@Immutable
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val selectedModel: LlmModel? = null,
    val systemPrompt: String? = null,
    val conversationId: String? = null,
    val isStreamingResponse: Boolean = false,
    val error: DomainError? = null,          // текущая ошибка, если есть
    val lastUserInput: String = "",           // для retry (опционально)
    val attachedFiles: List<FileAttachment> = emptyList(), // для retry
)

private const val RETRY_REPLACE_TIMEOUT_MS = 120_000L

class ChatViewModel(
    private val shareService: ShareService,
    private val interactor: ILLMInteractor,
    private val fileRepository: IFileRepository,
    private val modelRepository: ModelRepository,
    private val conversationId: String?,
    private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()
) : ViewModel() {
    private val sendMutex = Mutex()

    private val _isSending = MutableStateFlow(false)
    private val _isStreamingResponse = MutableStateFlow(false)
    /** Накопленный reasoning текущего ответа (в БД не сохраняется). */
    private val _thinking = MutableStateFlow("")
    val thinking: kotlinx.coroutines.flow.StateFlow<String> get() = _thinking
    private val _uiEvents = MutableSharedFlow<ChatUiEvent>()
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    /** Метрики последнего ответа (в БД не сохраняются). */
    private val _responseMetrics = MutableStateFlow<ResponseMetrics?>(null)
    val responseMetrics: StateFlow<ResponseMetrics?> = _responseMetrics

    private val _currentInputText = MutableStateFlow(savedState.get<String>("chat-input").orEmpty())
    val currentInputText: StateFlow<String> = _currentInputText

    private val _attachedFiles = MutableStateFlow<List<FileAttachment>>(emptyList())
    val attachedFiles: StateFlow<List<FileAttachment>> = _attachedFiles

    private val _screenConfig = MutableStateFlow(
        ScreenConfig(
            title = "Chat",
            showBackButton = true,
            actions = listOf(
                AppBarAction(
                    Icons.Default.Share,
                    showIcon = true,
                    contentDescription = "Share"
                ) { onExportChatClicked() },
                AppBarAction(
                    Icons.Default.ImportExport,
                    showIcon = true,
                    contentDescription = "Export"
                ) { exportChat() },
                AppBarAction(
                    Icons.Default.Clear,
                    showIcon = false,
                    contentDescription = "Clear"
                ) { onConfirmClearChat() },
                AppBarAction(
                    Icons.Default.Collections,
                    showIcon = false,
                    contentDescription = "System"
                ) { onSystemPrompt() },
                AppBarAction(
                    Icons.Default.Assistant,
                    showIcon = false,
                    contentDescription = "Model"
                ) { onModelsClick() },
                AppBarAction(
                    Icons.Default.Assistant,
                    showIcon = false,
                    isTitleAction = true,
                    contentDescription = "Model in Title"
                ) { onModelsClick() },
            )
        )
    )

    val screenConfig = _screenConfig.asStateFlow()

    private fun onModelsClick() {
        viewModelScope.launch {
            _uiEvents.emit(ChatUiEvent.NavigateModels)
        }
    }

    private var currentRequestJob: Job? = null

    private val _currentConversationId = MutableStateFlow(conversationId)

    // SharedFlow для одноразовой передачи нового conversationId в Fragment
    private val _newConversationIdEvent = MutableSharedFlow<String>(replay = 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = _currentConversationId.flatMapLatest { currentId ->
        // 1. Получаем потоки данных
        val historyFlow = if (currentId != null) {
            interactor.getChatHistoryFlow(currentId)
        } else {
            flowOf(emptyList())
        }

        val systemPromptFlow = if (currentId != null) {
            flow { emit(interactor.getSystemPrompt(currentId)) }
        } else {
            flowOf("")
        }

        // 2. Объединяем БЕЗ side-effects и БЕЗ лишних копирований
        combine(
            historyFlow,
            modelRepository.getSelectedModelFlow(),
            _isSending,
            _isStreamingResponse,
            systemPromptFlow
        ) { messages, modelResult, isLoading, isStreaming, systemPrompt ->

            // Извлекаем модель безопасно
            val model = (modelResult as? DataResult.Success)?.data

            // Создаем State.
            // ВАЖНО: messages передаем напрямую!
            ChatUiState(
                messages = messages,
                selectedModel = model,
                isSending = isLoading,
                systemPrompt = systemPrompt,
                isStreamingResponse = isStreaming,
                conversationId = currentId
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ChatUiState()
        )

    /**
     * Логирует каждую эмиссию потока.
     *
     */
    fun <T> Flow<T>.log(): Flow<T> = onEach { value ->
        val msg = when (value) {
            is ChatUiState -> value.toShortString()
            else -> value.toString()
        }
        println(msg)
    }

    /**
     * Псевдо‑вывод только ключевых полей для экономии места в логах.
     */
    fun ChatUiState.toShortString(): String {
        return buildString {
            append("loading=${_isSending.value}")
            if (messages.isNotEmpty()) {
                append("\nmsgCount=${messages.size},")
                if (!_isSending.value) {
                    append("\nmessages=${messages.last().content},")
                }
            }

            append("\nstreaming=$isStreamingResponse")
            append("\nmodel=${selectedModel?.name ?: "none"}")
            if (error != null) {
                append("\nerror=${error.stackTraceToString()}")
            }
        }
    }

    init {
        viewModelScope.launch { _currentInputText.collect { savedState["chat-input"] = it } }
        // Восстанавливаем размышления последнего ответа из БД (после перезапуска/смены чата).
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.isStreamingResponse || state.isSending) return@collect
                val stored = state.messages.lastOrNull { it.role != ChatRole.USER }?.reasoning.orEmpty()
                if (stored != _thinking.value && stored.isNotBlank()) _thinking.value = stored
            }
        }
        viewModelScope.launch {
            if (_currentConversationId.value == null) {
                _currentConversationId.value = interactor.createNewConversation("Новый чат")
            }
        }
        viewModelScope.launch {
            modelRepository.getSelectedModelFlow().collect { result ->
                if (result is DataResult.Error) {
                    result.error.printStackTrace()
                    _uiEvents.emit(
                        ChatUiEvent.ShowError(DomainError.generic(R.string.no_model_selected))
                    )
                }
            }
        }
        refreshModels()
    }

    private fun refreshModels() {
        viewModelScope.launch {
            modelRepository.refreshModels()
        }
    }

    private fun onConfirmClearChat() {
        viewModelScope.launch {
            _uiEvents.emit(ChatUiEvent.ConfirmClearChat)
        }
    }

    fun onRemoveChatHistory() {
        viewModelScope.launch {
            val conversationId = uiState.value.conversationId
            if (conversationId != null) {
                interactor.clearChat(conversationId)
            } else {
                _uiEvents.emit(ChatUiEvent.ShowError(DomainError.local(R.string.error_no_conversation_id)))
            }
        }
    }

    fun onSystemPrompt() {
        viewModelScope.launch {
            val value = _currentConversationId.value
            if (value != null) {
                _uiEvents.emit(ChatUiEvent.NavigateToSystem(value))
            } else {
                _uiEvents.emit(ChatUiEvent.ShowError(DomainError.local(R.string.error_no_conversation_id)))
            }
        }
    }

    private fun exportChat() {
        val currentId = _currentConversationId.value ?: return
        viewModelScope.launch {
            try {
                val chatContent = interactor.getFullChatForExport(currentId)
                shareService.exportFile("chat.txt", chatContent)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun onExportChatClicked() {
        val currentId = _currentConversationId.value ?: return
        viewModelScope.launch {
            try {
                val chatContent = interactor.getFullChatForExport(currentId)
                shareService.shareText(chatContent)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    private suspend fun ensureConversationExists(firstMessage: String): String {
        return _currentConversationId.value ?: run {
            val newId = interactor.createNewConversation(
                firstMessage.take(40).ifEmpty { "Новый чат" }
            )
            _currentConversationId.value = newId
            _newConversationIdEvent.emit(newId) // Уведомляем UI (Fragment) об обновлении
            newId
        }
    }

    fun clearInput() {
        _currentInputText.value = ""
    }

    /**
     * Отправляет сообщение пользователя.
     *
     * @param userMessageText Текст сообщения.
     * @param skipUserMessageCreation Если true, не создает сообщение пользователя в БД
     *                                (используется для retry/regenerate).
     */
    fun sendMessage(userMessageText: String, skipUserMessageCreation: Boolean = false) {
        // Текст может отсутствовать, если отправляют только вложение.
        val hasAttachments = _attachedFiles.value.isNotEmpty()
        if (userMessageText.isBlank() && !hasAttachments) return
        val textToSend = userMessageText.ifBlank {
            _attachedFiles.value.joinToString(", ") { it.fileName }.let { "📎 $it" }
        }

        viewModelScope.launch {
            currentRequestJob?.cancel()
            val (convId, filesToSend) = sendMutex.withLock {
                _isSending.value = true
                val files = _attachedFiles.value.toList()
                _attachedFiles.value = emptyList()
                val id = ensureConversationExists(textToSend)
                Pair(id, files)
            }

            try {
                _isStreamingResponse.value = true
                _responseMetrics.value = null

                currentRequestJob = viewModelScope.launch {
                    val startedAt = System.nanoTime()
                    var firstActivityAt: Long? = null
                    val reasoning = StringBuilder()
                    try {
                        _thinking.value = ""
                        var lastEmit = 0L
                        interactor.sendMessageWithFallback(
                            conversationId = convId,
                            userMessageText = textToSend,
                            attachedFiles = filesToSend,
                            skipUserMessageCreation = skipUserMessageCreation,
                            onReasoning = { chunk ->
                                if (firstActivityAt == null) firstActivityAt = System.nanoTime()
                                reasoning.append(chunk)
                                val now = System.nanoTime()
                                // Троттлинг: reasoning приходит мелкими чанками, UI обновляем ~5 раз в секунду.
                                if (now - lastEmit > 200_000_000L) {
                                    lastEmit = now
                                    _thinking.value = reasoning.toString()
                                }
                            }
                        )
                    } catch (e: CancellationException) {   // propagate cancellation
                        throw e
                    } catch (e: Exception) {
                        // Ошибка доставки: возвращаем текст и вложения в поле ввода,
                        // чтобы пользователь не набирал сообщение заново.
                        if (userMessageText.isNotBlank()) updateInputText(userMessageText)
                        if (filesToSend.isNotEmpty()) _attachedFiles.value = filesToSend
                        _uiEvents.emit(ChatUiEvent.ShowError(e))
                    } finally {
                        // Досылаем хвост reasoning: иначе последний throttled-фрагмент теряется.
                        _thinking.value = reasoning.toString()
                        val endedAt = System.nanoTime()
                        val totalMs = (endedAt - startedAt) / 1_000_000
                        val generationMs = ((endedAt - (firstActivityAt ?: startedAt)) / 1_000_000).coerceAtLeast(1)
                        val chars = runCatching {
                            interactor.getChatHistoryFlow(convId).first()
                                .lastOrNull { it.role != ChatRole.USER }?.content?.length ?: 0
                        }.getOrDefault(0)
                        if (totalMs > 0) {
                            _responseMetrics.value = ResponseMetrics(
                                totalMs = totalMs,
                                chars = chars,
                                charsPerSecond = if (chars > 0) (chars * 1000L / generationMs).toInt() else 0,
                            )
                        }
                    }
                }

                clearInput()
                currentRequestJob?.join()
            } finally {
                sendMutex.withLock {
                    _isSending.value = false
                    _isStreamingResponse.value = false
                }
            }
        }
    }

    fun cancelCurrentRequest() {
        currentRequestJob?.cancel()
        currentRequestJob = null
        _isSending.value = false
        _isStreamingResponse.value = false
        interactor.cancelCurrentRequest()
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentRequest()
    }

    /**
     * Обновить текущий текст ввода для расчета токенов
     */
    fun updateInputText(text: String) {
        _currentInputText.value = text
    }

    /**
     * Удалить файл из расчета токенов
     */
    fun removeAttachedFile(fileId: String) {
        _attachedFiles.value = _attachedFiles.value.filter { it.id != fileId }
    }


    /**
     * Копирование текста сообщения.
     * Лучше вызывать shareService, чтобы не тащить Context во ViewModel.
     */
    fun onCopyText(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                shareService.copyToClipboard(content)
                // Опционально: показываем тост, если сервис сам этого не делает
                // _uiEvents.emit(ChatUiEvent.ShowMessage("Текст скопирован"))
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * Поделиться текстом сообщения через системный интент.
     */
    fun onShareMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                shareService.shareText(content)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * Редактирование сообщения.
     * Берет текст сообщения и вставляет его в поле ввода.
     * Опционально: можно удалять старое сообщение, если логика чата это подразумевает,
     * но чаще всего это просто "Copy to input".
     */
    fun onEditMessage(content: String) {
        updateInputText(content)
    }

    /**
     * Повтор или перегенерация ответа.
     * 1. Если последнее сообщение - User (и произошла ошибка), просто отправляем его снова.
     * 2. Если последнее сообщение - Assistant, удаляем его и отправляем предыдущий промпт заново.
     *
     * ВАЖНО: При retry/regenerate НЕ создаем новое сообщение пользователя (skipUserMessageCreation = true),
     * чтобы избежать дублирования сообщений в чате.
     */
    fun onRetryMessage() {
        if (_isSending.value) return

        val history = uiState.value.messages
        if (history.isEmpty()) return

        val lastMessage = history.last()

        viewModelScope.launch {
            // Сценарий 1: Последнее сообщение от ИИ (Regenerate)
            if (lastMessage.role != ChatRole.USER) {
                val lastUserMessage = history.findLast { it.role == ChatRole.USER } ?: return@launch
                val staleId = lastMessage.id

                sendMessage(
                    userMessageText = lastUserMessage.content,
                    skipUserMessageCreation = true
                )

                // Старый ответ удаляем только после успешной замены: при ошибке генерации
                // пользователь не должен потерять уже полученный текст.
                val replaced = withTimeoutOrNull(RETRY_REPLACE_TIMEOUT_MS) {
                    uiState
                        .map { state -> state.messages }
                        .first { messages ->
                            messages.any { it.role == ChatRole.ASSISTANT && it.id != staleId && it.content.isNotBlank() }
                        }
                    true
                }
                if (replaced == true) {
                    runCatching { interactor.deleteMessage(staleId) }
                } else {
                    _uiEvents.emit(
                        ChatUiEvent.ShowMessage("Прежний ответ сохранён: замена не удалась")
                    )
                }
            }
            // Сценарий 2: Последнее сообщение от User (Network Error / Retry)
            // Просто повторяем отправку последнего сообщения пользователя
            else {
                sendMessage(
                    userMessageText = lastMessage.content,
                    skipUserMessageCreation = true
                )
            }
        }
    }

    /**
     * Удаляет сообщение и все сообщения после него.
     * Используется для удаления сообщения пользователя и последующей истории.
     */
    fun onDeleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                val messages = uiState.value.messages
                val messageIndex = messages.indexOfFirst { it.id == messageId }

                if (messageIndex == -1) return@launch

                // Удаляем сообщение и все после него
                val messagesToDelete = messages.subList(messageIndex, messages.size)
                messagesToDelete.forEach { message ->
                    interactor.deleteMessage(message.id)
                }

                _uiEvents.emit(ChatUiEvent.ShowMessage("Сообщения удалены"))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * Редактирует сообщение пользователя.
     * Удаляет старое сообщение и все после него, вставляет текст в поле ввода.
     */
    fun onEditUserMessage(messageId: String, currentContent: String) {
        viewModelScope.launch {
            try {
                // Удаление выполняем здесь и дожидаемся его: onDeleteMessage запускает свою
                // корутину, из-за чего ввод мог оказаться в чате с ещё не удалённой историей.
                val messages = uiState.value.messages
                val index = messages.indexOfFirst { it.id == messageId }
                if (index != -1) {
                    messages.subList(index, messages.size).forEach { message ->
                        interactor.deleteMessage(message.id)
                    }
                }

                // Вставляем текст в поле ввода для редактирования
                updateInputText(currentContent)

                _uiEvents.emit(ChatUiEvent.ShowMessage("Отредактируйте сообщение и отправьте"))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * Обрабатывает выбранный файл из file picker.
     * Читает файл и добавляет его в список прикрепленных файлов.
     */
    fun processFileUri(uri: Uri) {
        viewModelScope.launch {
            fileRepository.processFileFromUri(uri)
                .collect { result ->
                    when (result) {
                        is FileProcessingResult.Complete -> {
                            // Добавляем файл в список прикрепленных
                            _attachedFiles.value = _attachedFiles.value + result.fileAttachment
                        }

                        is FileProcessingResult.Error -> {
                            // Показываем ошибку пользователю
                            _uiEvents.emit(ChatUiEvent.ShowMessage("Ошибка файла: ${result.message}"))
                        }

                        else -> {
                            // Progress и Started - можно игнорировать или добавить прогресс UI
                        }
                    }
                }
        }
    }
}
