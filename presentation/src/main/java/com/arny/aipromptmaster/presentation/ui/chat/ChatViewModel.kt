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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ChatUiEvent {
    data class ShowError(val error: Throwable) : ChatUiEvent()
    data class ShareChat(val content: String) : ChatUiEvent()
    data object RequestClearChat : ChatUiEvent()
}

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
    private val fileRepository: IFileRepository,
    @Assisted private val conversationId: String?
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _isStreamingResponse = MutableStateFlow(false)
    private val _uiEvents = MutableSharedFlow<ChatUiEvent>()
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    // Переменные для отслеживания токенов
    private val _estimatedTokens = MutableStateFlow(0)
    val estimatedTokens: StateFlow<Int> = _estimatedTokens

    private val _currentInputText = MutableStateFlow("")

    private val _attachedFiles = MutableStateFlow<List<FileAttachment>>(emptyList())
    val attachedFiles: StateFlow<List<FileAttachment>> = _attachedFiles

    // Точность расчетов (точный/примерный)
    private val _isAccurate = MutableStateFlow(false)
    val isAccurate: StateFlow<Boolean> = _isAccurate

    private var currentRequestJob: kotlinx.coroutines.Job? = null

    private val _currentConversationId = MutableStateFlow(conversationId)

    // SharedFlow для одноразовой передачи нового conversationId в Fragment
    private val _newConversationIdEvent = MutableSharedFlow<String>(replay = 0)
    val newConversationIdEvent: SharedFlow<String> = _newConversationIdEvent.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = _currentConversationId.flatMapLatest { currentId ->
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
            _isLoading,
            _isStreamingResponse,
            systemPromptFlow
        ) { messages, modelResult, isLoading, isStreamingResponse, systemPrompt ->
            ChatUiState(
                messages = messages,
                selectedModel = (modelResult as? DataResult.Success)?.data,
                isLoading = isLoading,
                systemPrompt = systemPrompt,
                isStreamingResponse = isStreamingResponse,
                conversationId = currentId
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversationFiles: StateFlow<List<FileAttachment>> =
        _currentConversationId.flatMapLatest { currentId ->
            if (currentId != null) {
                interactor.getConversationFilesFlow(currentId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

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

    /**
     * ✅ Удалить файл из чата
     */
    fun removeFileFromChat(fileId: String) {
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.removeFileFromConversation(currentId, fileId)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Обработка файлов
     */
    fun processFileFromUri(uri: Uri): Flow<FileProcessingResult> {
        return fileRepository.processFileFromUri(uri)
            .onEach { result ->
                if (result is FileProcessingResult.Complete) {
                    try {
                        // 1. Сохраняем файл во временное хранилище
                        fileRepository.saveTemporaryFile(result.fileAttachment)

                        // 2. Добавляем к чату (если чат создан)
                        _currentConversationId.value?.let { convId ->
                            interactor.addFileToConversation(convId, result.fileAttachment)
                        } ?: run {
                            // Если чата нет, добавляем в локальный список
                            addAttachedFile(result.fileAttachment)
                        }
                    } catch (e: Exception) {
                        _uiEvents.emit(ChatUiEvent.ShowError(e))
                    }
                }
            }
            .catch { exception ->
                emit(FileProcessingResult.Error(exception.message ?: "Unknown error"))
                _uiEvents.emit(ChatUiEvent.ShowError(exception))
            }
    }

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank() || _isLoading.value) return

        currentRequestJob?.cancel()
        currentRequestJob = viewModelScope.launch {
            _isLoading.value = true
            _isStreamingResponse.value = false

            try {
                // 1. Создаем или получаем conversationId
                val currentConversationId = _currentConversationId.value ?: run {
                    val newTitle = userMessageText.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    newId
                }

                // 2. ПРОСТО добавляем user message (файлы УЖЕ в чате)
                interactor.addUserMessageToHistory(
                    currentConversationId,
                    userMessageText
                )

                // 3. Получаем модель
                val selectedModel = (selectedModelResult.value as? DataResult.Success)?.data
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                // 4. Отправляем запрос (файлы автоматически подтягиваются из чата)
                _isStreamingResponse.value = true
                interactor.sendMessage(
                    model = selectedModel.id,
                    conversationId = currentConversationId,
                    estimatedTokens = _estimatedTokens.value
                )

            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            } finally {
                _isLoading.value = false
                _isStreamingResponse.value = false
                currentRequestJob = null
            }
        }
    }

    /**
     * Регенерация последнего ответа
     */
    fun regenerateLastResponse() {
        if (_isLoading.value) return

        val currentConversationId = _currentConversationId.value ?: return

        currentRequestJob?.cancel()
        currentRequestJob = viewModelScope.launch {
            _isLoading.value = true
            _isStreamingResponse.value = false

            try {
                val selectedModel = (selectedModelResult.value as? DataResult.Success)?.data
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                _isStreamingResponse.value = true
                interactor.regenerateLastResponse(
                    model = selectedModel.id,
                    conversationId = currentConversationId
                )

            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            } finally {
                _isLoading.value = false
                _isStreamingResponse.value = false
                currentRequestJob = null
            }
        }
    }

    /**
     * Регенерация конкретного сообщения
     */
    fun regenerateMessage(messageId: String) {
        if (_isLoading.value) return

        val currentConversationId = _currentConversationId.value ?: return

        currentRequestJob?.cancel()
        currentRequestJob = viewModelScope.launch {
            _isLoading.value = true
            _isStreamingResponse.value = false

            try {
                val selectedModel = (selectedModelResult.value as? DataResult.Success)?.data
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                _isStreamingResponse.value = true
                interactor.regenerateMessage(
                    model = selectedModel.id,
                    conversationId = currentConversationId,
                    messageId = messageId
                )

            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            } finally {
                _isLoading.value = false
                _isStreamingResponse.value = false
                currentRequestJob = null
            }
        }
    }

    fun setSystemPrompt(prompt: String) {
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.setSystemPrompt(currentId, prompt)
                calculateEstimatedTokens()
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

    fun cancelCurrentRequest() {
        currentRequestJob?.cancel()
        currentRequestJob = null
        _isLoading.value = false
        _isStreamingResponse.value = false
        interactor.cancelCurrentRequest()
    }

    /**
     * Обрабатывает нажатие на меню системного промпта
     * Создает новый чат если текущий пустой
     */
    fun onSystemPromptMenuClicked() {
        viewModelScope.launch {
            try {
                val currentId = _currentConversationId.value

                if (currentId == null) {
                    // Если чат пустой, создаем новый чат
                    val newTitle = "Новый чат"
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    _newConversationIdEvent.emit(newId)
                } else {
                    // Если чат существует, просто передаем его ID
                    _newConversationIdEvent.emit(currentId)
                }
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    /**
     * Обновить текущий текст ввода для расчета токенов
     */
    fun updateInputText(text: String) {
        _currentInputText.value = text
        calculateEstimatedTokens()
    }

    /**
     * Добавить файл для расчета токенов
     */
    fun addAttachedFile(file: FileAttachment) {
        _attachedFiles.value = _attachedFiles.value + file
        calculateEstimatedTokens()
    }

    /**
     * Рассчитать примерное количество токенов
     * Примерно 4 символа на токен
     */
    private fun calculateEstimatedTokens() {
        viewModelScope.launch {
            try {
                val inputText = _currentInputText.value
                val attachedFiles = _attachedFiles.value
                val conversationId = _currentConversationId.value

                val result = interactor.estimateTokensForCurrentChat(
                    inputText = inputText,
                    attachedFiles = attachedFiles,
                    conversationId = conversationId
                )

                _estimatedTokens.value = result.estimatedTokens
                _isAccurate.value = result.isAccurate
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }

    fun onClearChatClicked() {
        viewModelScope.launch {
            _uiEvents.emit(ChatUiEvent.RequestClearChat)
        }
    }
}
