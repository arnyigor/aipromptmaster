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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach  // ✅ ВАЖНЫЙ ИМПОРТ
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _isLoading = MutableStateFlow(false)
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

    // Коэффициент для расчета токенов (символов на токен)
    private val _tokenRatio = MutableStateFlow(4.0)
    val tokenRatio: StateFlow<Double> = _tokenRatio

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
     * ✅ НОВЫЙ ПУБЛИЧНЫЙ МЕТОД: Добавить сообщение с файлом
     * Делегирует вызов к interactor
     */
    suspend fun addMessageWithFile(
        conversationId: String,
        userMessage: String,
        fileAttachment: FileAttachment
    ) {
        try {
            interactor.addUserMessageWithFile(conversationId, userMessage, fileAttachment)
        } catch (e: Exception) {
            _uiEvents.emit(ChatUiEvent.ShowError(e))
            throw e
        }
    }

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank() || _isLoading.value) return

        // Отменяем предыдущий запрос, если он есть
        currentRequestJob?.cancel()
        currentRequestJob = null

        currentRequestJob = viewModelScope.launch {
            _isLoading.value = true
            _isStreamingResponse.value = false
            try {
                val conversationId = _currentConversationId.value ?: run {
                    val newTitle = userMessageText.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    newId
                }

                // Обрабатываем прикрепленные файлы, если они есть
                val attachedFiles = _attachedFiles.value
                if (attachedFiles.isNotEmpty()) {
                    // Если есть файлы, добавляем их в историю через специальный метод
                    attachedFiles.forEach { file ->
                        interactor.addUserMessageWithFile(conversationId, userMessageText, file)
                    }
                    // Очищаем прикрепленные файлы после добавления в историю
                    _attachedFiles.value = emptyList()
                } else {
                    // Добавляем обычное пользовательское сообщение только если нет файлов
                    interactor.addUserMessageToHistory(conversationId, userMessageText)
                }

                val selectedModelResult = selectedModelResult.value
                val selectedModel = (selectedModelResult as? DataResult.Success)?.data
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                _isStreamingResponse.value = true

                // Сохраняем оценочное количество токенов до отправки для корректировки коэффициента
                val estimatedTokensBeforeRequest = _estimatedTokens.value

                // Используем sendMessageWithFallback, который правильно обрабатывает файлы
                interactor.sendMessageWithFallback(selectedModel.id, conversationId)

                // После получения ответа корректируем коэффициент (упрощенная версия)
                // В реальном приложении нужно извлекать usage из ответа
                // Здесь предполагаем, что корректировка происходит в interactor

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
     * ✅ ЕДИНСТВЕННАЯ функция для обработки файлов через fileProcessingService.
     * Делегирует обработку в FileRepository, который использует FileProcessingService.
     * Автоматически проверяет тип файла и обрабатывает только текстовые файлы.
     * @param uri URI выбранного файла
     * @return Flow с результатами обработки по чанкам
     */
    fun processFileFromUri(uri: Uri): Flow<FileProcessingResult> {

        return fileRepository.processFileFromUri(uri)
            .onEach { result ->
                // Автоматически сохраняем завершенный файл в репозиторий и добавляем в разговор
                if (result is FileProcessingResult.Complete) {
                    try {
                        // ✅ ТОЛЬКО сохраняем файл, НЕ добавляем в историю
                        fileRepository.saveTemporaryFile(result.fileAttachment)
                    } catch (e: Exception) {
                        _uiEvents.emit(ChatUiEvent.ShowError(e))
                    }
                }
            }
            .catch { exception ->
                // Преобразуем любые ошибки в FileProcessingResult.Error
                emit(FileProcessingResult.Error(exception.message ?: "Unknown error"))
                _uiEvents.emit(ChatUiEvent.ShowError(exception))
            }
    }

    /**
     * Сохранить файл во временном репозитории и вернуть его ID.
     * @return ID сохраненного файла
     */
    fun saveFileAttachment(fileAttachment: FileAttachment): String {
        viewModelScope.launch {
            try {
                fileRepository.saveTemporaryFile(fileAttachment)
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
        return fileAttachment.id
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
     * Удалить файл из расчета токенов
     */
    fun removeAttachedFile(fileId: String) {
        _attachedFiles.value = _attachedFiles.value.filter { it.id != fileId }
        calculateEstimatedTokens()
    }

    /**
     * Очистить все прикрепленные файлы
     */
    fun clearAttachedFiles() {
        _attachedFiles.value = emptyList()
        calculateEstimatedTokens()
    }

    /**
     * Рассчитать примерное количество токенов
     * Примерно 4 символа на токен
     */
   private fun calculateEstimatedTokens() {
       val ratio = _tokenRatio.value
       var totalTokens = 0

       // Токены от текста ввода
       val inputText = _currentInputText.value
       if (inputText.isNotBlank()) {
           totalTokens += ceil(inputText.length / ratio).toInt()
       }

       // Токены от прикрепленных файлов
       _attachedFiles.value.forEach { file ->
           if (file.mimeType.startsWith("text/")) {
               totalTokens += ceil(file.originalContent.length / ratio).toInt()
           }
       }

       // Токены от системного промпта
       val systemPrompt = uiState.value.systemPrompt
       if (!systemPrompt.isNullOrBlank()) {
           totalTokens += ceil(systemPrompt.length / ratio).toInt()
       }

       // Токены от истории сообщений (последние 10)
       uiState.value.messages.takeLast(10).forEach { message ->
           totalTokens += ceil(message.content.length / ratio).toInt()
       }

       _estimatedTokens.value = totalTokens
   }

   /**
    * Скорректировать коэффициент расчета на основе реальных данных из API
    * @param actualPromptTokens реальное количество токенов во входящем сообщении
    * @param actualCompletionTokens реальное количество токенов в ответе
    * @param estimatedPromptTokens оценочное количество токенов во входящем сообщении
    */
   fun adjustTokenRatio(
       actualPromptTokens: Int,
       actualCompletionTokens: Int,
       estimatedPromptTokens: Int
   ) {
       if (actualPromptTokens > 0 && estimatedPromptTokens > 0) {
           // Рассчитываем новый коэффициент на основе реальных данных
           val currentRatio = _tokenRatio.value
           val newRatio = actualPromptTokens.toDouble() / estimatedPromptTokens.toDouble()

           // Плавная корректировка коэффициента (смешивание с текущим значением)
           val adjustedRatio = (currentRatio * 0.7) + (newRatio * 0.3)

           // Ограничиваем диапазон коэффициента
           val clampedRatio = adjustedRatio.coerceIn(2.0, 8.0)

           _tokenRatio.value = clampedRatio
           _isAccurate.value = true

           // Пересчитываем токены с новым коэффициентом
           calculateEstimatedTokens()
       }
   }

}
