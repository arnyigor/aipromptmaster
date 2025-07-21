package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
    @Assisted private val conversationId: String?
) : ViewModel() {
    private val _sendingState = MutableStateFlow(SendingState())

    // Храним ID текущего диалога
    private val _currentConversationId = MutableStateFlow(conversationId)

    // ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ ДЛЯ UI
    val uiState: StateFlow<ChatUiState> = _currentConversationId.flatMapLatest { currentId ->
        // Если ID null, мы просто возвращаем пустой поток. UI покажет пустой чат.
        val historyFlow = if (currentId != null) {
            interactor.getChatHistoryFlow(currentId)
        } else {
            flowOf(emptyList())
        }
        combine(
            historyFlow,
            interactor.getSelectedModel(),
            _sendingState
        ) { messages, modelResult, sendingState ->
            val selectedModel = (modelResult as? DataResult.Success)?.data
            val modelError = (modelResult as? DataResult.Error)?.exception
            ChatUiState(
                messages = messages,
                selectedModel = selectedModel,
                isLoading = sendingState.isLoading,
                error = sendingState.error ?: modelError
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatUiState() // Начальное пустое состояние
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

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) return

        viewModelScope.launch {
            _sendingState.update { it.copy(isLoading = true) }

            try {
                // 1. Убеждаемся, что у нас есть ID. Если нет - СОЗДАЕМ.
                val conversationId = _currentConversationId.value ?: run {
                    val newTitle = userMessageText.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId // Обновляем состояние
                    newId
                }

                // 2. Теперь у нас ГАРАНТИРОВАННО есть ID. Добавляем сообщение пользователя.
                interactor.addUserMessageToHistory(conversationId, userMessageText)

                // 3. Получаем контекст для API
                val messagesForApi = interactor.getChatHistoryFlow(conversationId)
                    .first()
                    .takeLast(20)

                val selectedModelId = uiState.value.selectedModel?.id
                if (selectedModelId == null) {
                    throw DomainError.Local("Модель не выбрана")
                }

                // 4. Отправляем запрос в API
                interactor.sendMessage(selectedModelId, messagesForApi)
                    .collect { result ->
                        when (result) {
                            is DataResult.Success -> {
                                // 5. При успехе добавляем ответ ассистента в тот же чат
                                interactor.addAssistantMessageToHistory(conversationId, result.data)
                                _sendingState.update { it.copy(isLoading = false, error = null) }
                            }
                            is DataResult.Error -> throw result.exception!!
                            DataResult.Loading -> { /* Уже обрабатывается в начале */ }
                        }
                    }
            } catch (e: Exception) {
                _sendingState.update { it.copy(isLoading = false, error = e) }
            }
        }
    }


    fun errorShown() {
        _sendingState.update { it.copy(error = null) }
    }
}