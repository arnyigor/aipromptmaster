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
import kotlinx.coroutines.flow.MutableStateFlow
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

class ChatViewModel(
    private val shareService: ShareService,
    private val interactor: ILLMInteractor,
    private val fileRepository: IFileRepository,
    private val modelRepository: ModelRepository,
    private val conversationId: String?
) : ViewModel() {
    private val sendMutex = Mutex()

    private val _isSending = MutableStateFlow(false)
    private val _isStreamingResponse = MutableStateFlow(false)
    private val _uiEvents = MutableSharedFlow<ChatUiEvent>()
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    // Переменные для отслеживания токенов
    private val _estimatedTokens = MutableStateFlow(0)
    val estimatedTokens: StateFlow<Int> = _estimatedTokens

    private val _currentInputText = MutableStateFlow("")
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
        if (userMessageText.isBlank()) return

        viewModelScope.launch {
            currentRequestJob?.cancel()
            val (convId, filesToSend) = sendMutex.withLock {
                _isSending.value = true
                val files = _attachedFiles.value.toList()
                _attachedFiles.value = emptyList()
                val id = ensureConversationExists(userMessageText)
                Pair(id, files)
            }

            try {
                _isStreamingResponse.value = true

                currentRequestJob = viewModelScope.launch {
                    try {
                        interactor.sendMessageWithFallback(
                            conversationId = convId,
                            userMessageText = userMessageText,
                            attachedFiles = filesToSend,
                            skipUserMessageCreation = skipUserMessageCreation
                        )
                    } catch (e: CancellationException) {   // propagate cancellation
                        throw e
                    } catch (e: Exception) {
                        _uiEvents.emit(ChatUiEvent.ShowError(e))
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
        calculateEstimatedTokens()
    }

    /**
     * Удалить файл из расчета токенов
     */
    fun removeAttachedFile(fileId: String) {
        _attachedFiles.value = _attachedFiles.value.filter { it.id != fileId }
        calculateEstimatedTokens()
    }

    /**
     * Рассчитать примерное количество токенов
     * Примерно 4 символа на токен
     */
    private fun calculateEstimatedTokens() {
        viewModelScope.launch {
            _estimatedTokens.value = interactor.estimateTokens(
                inputText = _currentInputText.value,
                attachedFiles = _attachedFiles.value,
                systemPrompt = uiState.value.systemPrompt,
                recentMessages = uiState.value.messages.takeLast(10)
            )
        }
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
            // Мы хотим удалить этот ответ и заставить модель ответить заново на предыдущий вопрос.
            if (lastMessage.role != ChatRole.USER) {
                // Находим последний запрос пользователя
                val lastUserMessage = history.findLast { it.role == ChatRole.USER } ?: return@launch

                // Удаляем ответ модели из БД, чтобы не было дублей
                try {
                    interactor.deleteMessage(lastMessage.id)
                } catch (e: Exception) {
                    // Если удаление не сработало, логируем но продолжаем
                    e.printStackTrace()
                }

                // Отправляем запрос заново, НО не создаем новое сообщение пользователя
                sendMessage(
                    userMessageText = lastUserMessage.content,
                    skipUserMessageCreation = true
                )
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
                // Удаляем сообщение и все после него
                onDeleteMessage(messageId)

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
                            calculateEstimatedTokens()
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
