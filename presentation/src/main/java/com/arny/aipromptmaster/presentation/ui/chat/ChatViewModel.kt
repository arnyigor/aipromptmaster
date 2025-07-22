package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    @OptIn(ExperimentalCoroutinesApi::class)
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
            // Устанавливаем состояние загрузки в самом начале.
            // Также очищаем предыдущую ошибку, чтобы она не "зависла" в UI.
            _sendingState.update { it.copy(isLoading = true, error = null) }

            // Мы используем runCatching, чтобы элегантно обработать ошибки
            // из СИНХРОННОЙ части кода (создание чата, проверка модели).
            val result: Result<Unit> = runCatching {
                // 1. Убеждаемся, что у нас есть ID. Если нет - СОЗДАЕМ.
                val conversationId = _currentConversationId.value ?: run {
                    val newTitle = userMessageText.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId // Обновляем состояние
                    newId
                }

                // 2. Добавляем сообщение пользователя.
                interactor.addUserMessageToHistory(conversationId, userMessageText)

                val selectedModelId = uiState.value.selectedModel?.id
                    ?: throw DomainError.Local("Модель не выбрана. Пожалуйста, выберите модель в настройках.")

                interactor.sendMessageWithFallback(selectedModelId, conversationId)
            }

            _sendingState.update { it.copy(isLoading = false) }

            // Если в блоке runCatching произошла синхронная ошибка (до вызова Flow),
            // мы перехватим ее здесь.
            result.onFailure { exception ->
                _sendingState.update { it.copy(isLoading = false, error = exception) }
            }
        }
    }


    fun errorShown() {
        _sendingState.update { it.copy(error = null) }
    }

    fun onRemoveChatHistory() {
        // Получаем текущий ID. Если его нет, то и очищать нечего.
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.clearChat(currentId)
                // Если нужно показать сообщение об успехе, можно использовать для этого
                // поле error с каким-то специальным типом Success.
                // Например:
                // _sendingState.update { it.copy(error = DomainSuccess("История чата очищена")) }
            } catch (e: Exception) {
                // Если интерактор может выбросить исключение, ловим его здесь
                // и обновляем состояние с ошибкой.
                _sendingState.update { it.copy(isLoading = false, error = e) }
            }
        }
    }
}