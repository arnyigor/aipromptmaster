package com.arny.aipromptmaster.presentation.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import com.arny.aipromptmaster.presentation.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.ceil

sealed class ChatUiEvent {
    data class ShowError(val error: Throwable) : ChatUiEvent()
    data class ShareChat(val content: String) : ChatUiEvent()
}

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
    private val fileRepository: IFileRepository,
    @Assisted private val conversationId: String?
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow(conversationId)

    // ============================================
    // СОСТОЯНИЕ ГЕНЕРАЦИИ (sealed class)
    // ============================================
    private val _chatState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    // ============================================
    // ДАННЫЕ ЧАТА (data class)
    // ============================================
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatData: StateFlow<ChatData> = _currentConversationId.flatMapLatest { currentId ->
        val historyFlow = if (currentId != null) {
            interactor.getChatHistoryFlow(currentId)
        } else {
            flowOf(emptyList())
        }

        val systemPromptFlow = if (currentId != null) {
            flow<String?> { emit(interactor.getSystemPrompt(currentId)) }
        } else {
            flowOf(null)
        }

        combine(
            historyFlow,
            interactor.getSelectedModel(),
            systemPromptFlow
        ) { messages, modelResult, systemPrompt ->
            ChatData(
                messages = messages,
                selectedModel = (modelResult as? DataResult.Success)?.data,
                systemPrompt = systemPrompt,
                conversationId = currentId
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatData()
    )

    // ============================================
    // ВЛОЖЕНИЯ (для chips)
    // ============================================
    private val _fileAttachments = MutableStateFlow<Map<String, FileAttachment>>(emptyMap())

    val attachments: StateFlow<List<UiAttachment>> = _fileAttachments
        .map { fileMap ->
            fileMap.values.map { file -> file.toUiAttachment() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // ============================================
    // СОБЫТИЯ
    // ============================================
    private val _uiEvents = MutableSharedFlow<ChatUiEvent>(replay = 0)
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    private val _newConversationIdEvent = MutableSharedFlow<String>(replay = 0)
    val newConversationIdEvent: SharedFlow<String> = _newConversationIdEvent.asSharedFlow()

    // ============================================
    // ДОПОЛНИТЕЛЬНЫЕ ПОТОКИ
    // ============================================
    private val modelsResult: StateFlow<DataResult<List<LlmModel>>> = interactor.getModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )

    val selectedModelResult: StateFlow<DataResult<LlmModel>> = interactor.getSelectedModel()
        .catch { throwable ->
            emit(DataResult.Error(throwable))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )

    // Переменные для токенов
    private val _estimatedTokens = MutableStateFlow(0)
    val estimatedTokens: StateFlow<Int> = _estimatedTokens

    private val _tokenRatio = MutableStateFlow(4.0)
    val tokenRatio: StateFlow<Double> = _tokenRatio

    private val _isAccurate = MutableStateFlow(false)
    val isAccurate: StateFlow<Boolean> = _isAccurate

    private var currentRequestId: String? = null

    init {
        viewModelScope.launch {
            interactor.getSelectedModel().collect { result ->
                if (result is DataResult.Error) {
                    _uiEvents.emit(
                        ChatUiEvent.ShowError(
                            result.exception ?: DomainError.generic(R.string.no_model_selected)
                        )
                    )
                }
            }
        }

        // Автоматический пересчет токенов при изменении данных
        viewModelScope.launch {
            combine(
                chatData,
                _fileAttachments
            ) { _, _ ->
                calculateEstimatedTokens()
            }.collect {}
        }

        refreshModels()
    }

    private fun refreshModels() {
        viewModelScope.launch {
            val initialData = modelsResult.value
            if (initialData is DataResult.Loading ||
                (initialData is DataResult.Success && initialData.data.isEmpty())
            ) {
                interactor.refreshModels()
            }
        }
    }

    // ============================================
    // ОТПРАВКА СООБЩЕНИЙ
    // ============================================
    fun sendMessage(userMessageText: String) {
        val text = userMessageText.trim()

        if (text.isEmpty() && _fileAttachments.value.isEmpty()) return

        if (_chatState.value !is ChatUiState.Idle && _chatState.value !is ChatUiState.Completed) {
            return
        }

        if (hasUploadingFiles()) {
            viewModelScope.launch {
                _uiEvents.emit(
                    ChatUiEvent.ShowError(
                        DomainError.Generic("Дождитесь завершения загрузки файлов")
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            val requestId = UUID.randomUUID().toString()
            currentRequestId = requestId

            try {
                _chatState.value = ChatUiState.Streaming(requestId)

                val convId = _currentConversationId.value ?: run {
                    val newTitle = text.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    _newConversationIdEvent.emit(newId)
                    newId
                }

                val uploadedFiles = _fileAttachments.value.values.toList()

                if (uploadedFiles.isNotEmpty()) {
                    uploadedFiles.forEach { file ->
                        interactor.addUserMessageWithFile(convId, text, file)
                    }
                    _fileAttachments.value = emptyMap()
                } else {
                    interactor.addUserMessageToHistory(convId, text)
                }

                val selectedModelResult = selectedModelResult.value
                val selectedModel = (selectedModelResult as? DataResult.Success)?.data
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                interactor.sendMessageWithFallback(selectedModel.id, convId)

                _chatState.value = ChatUiState.Completed

            } catch (e: Exception) {
                _chatState.value = ChatUiState.Error(e.message ?: "Unknown error")
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            } finally {
                currentRequestId = null
            }
        }
    }

    fun cancelCurrentRequest() {
        currentRequestId?.let { requestId ->
            viewModelScope.launch {
                try {
                    interactor.cancelCurrentRequest()
                    _chatState.value = ChatUiState.Cancelled
                } catch (e: Exception) {
                    _chatState.value = ChatUiState.Error("Failed to cancel: ${e.message}")
                } finally {
                    currentRequestId = null
                }
            }
        }
    }

    // ============================================
    // РАБОТА С ФАЙЛАМИ
    // ============================================
    fun addAttachmentFromUri(uri: Uri) {
        if (_fileAttachments.value.size >= MAX_ATTACHMENTS) {
            viewModelScope.launch {
                _uiEvents.emit(
                    ChatUiEvent.ShowError(
                        DomainError.Generic("Максимум $MAX_ATTACHMENTS файлов")
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val attachmentId = UUID.randomUUID().toString()

                fileRepository.processFileFromUri(uri).collect { result ->
                    when (result) {
                        is FileProcessingResult.Started -> {
                            if (result.fileSize > MAX_FILE_SIZE) {
                                _chatState.value = ChatUiState.Error(
                                    "Файл превышает лимит ${MAX_FILE_SIZE / (1024 * 1024)} MB"
                                )
                                return@collect
                            }
                        }

                        is FileProcessingResult.Complete -> {
                            fileRepository.saveTemporaryFile(result.fileAttachment)
                            _fileAttachments.value = _fileAttachments.value +
                                    (attachmentId to result.fileAttachment)
                        }

                        is FileProcessingResult.Error -> {
                            _chatState.value = ChatUiState.Error(result.message)
                            _uiEvents.emit(ChatUiEvent.ShowError(Exception(result.message)))
                        }

                        else -> {}
                    }
                }

            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun removeAttachment(attachmentId: String) {
        _fileAttachments.value = _fileAttachments.value - attachmentId
    }

    fun hasUploadingFiles(): Boolean {
        return attachments.value.any {
            it.uploadStatus == UploadStatus.UPLOADING ||
                    it.uploadStatus == UploadStatus.PENDING
        }
    }

    // ============================================
    // ТОКЕНЫ
    // ============================================
    private fun calculateEstimatedTokens() {
        val ratio = _tokenRatio.value
        var totalTokens = 0

        _fileAttachments.value.values.forEach { file ->
            if (file.mimeType.startsWith("text/")) {
                totalTokens += ceil(file.originalContent.length / ratio).toInt()
            }
        }

        val systemPrompt = chatData.value.systemPrompt
        if (!systemPrompt.isNullOrBlank()) {
            totalTokens += ceil(systemPrompt.length / ratio).toInt()
        }

        chatData.value.messages.takeLast(10).forEach { message ->
            totalTokens += ceil(message.content.length / ratio).toInt()
        }

        _estimatedTokens.value = totalTokens
    }

    fun adjustTokenRatio(
        actualPromptTokens: Int,
        actualCompletionTokens: Int,
        estimatedPromptTokens: Int
    ) {
        if (actualPromptTokens > 0 && estimatedPromptTokens > 0) {
            val currentRatio = _tokenRatio.value
            val newRatio = actualPromptTokens.toDouble() / estimatedPromptTokens.toDouble()
            val adjustedRatio = (currentRatio * 0.7) + (newRatio * 0.3)
            val clampedRatio = adjustedRatio.coerceIn(2.0, 8.0)

            _tokenRatio.value = clampedRatio
            _isAccurate.value = true

            calculateEstimatedTokens()
        }
    }

    // ============================================
    // ОСТАЛЬНЫЕ МЕТОДЫ
    // ============================================
    fun onExportChatClicked() {
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                val chatContent = interactor.getFullChatForExport(currentId)
                _uiEvents.emit(ChatUiEvent.ShareChat(chatContent))
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun setSystemPrompt(prompt: String) {
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.setSystemPrompt(currentId, prompt)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun onRemoveChatHistory() {
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.clearChat(currentId)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun onSystemPromptMenuClicked() {
        viewModelScope.launch {
            try {
                val currentId = _currentConversationId.value

                if (currentId == null) {
                    val newTitle = "Новый чат"
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    _newConversationIdEvent.emit(newId)
                } else {
                    _newConversationIdEvent.emit(currentId)
                }
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    // ============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================
    private fun FileAttachment.toUiAttachment(): UiAttachment {
        return UiAttachment(
            id = this.id,
            displayName = this.fileName,
            mimeType = this.mimeType,
            size = this.fileSize,
            uploadStatus = UploadStatus.UPLOADED
        )
    }

    // ✅ УБРАЛИ createMessageItem - теперь он во Fragment

    companion object {
        private const val MAX_ATTACHMENTS = 5
        private const val MAX_FILE_SIZE = 30 * 1024 * 1024L // 30 MB
    }
}
