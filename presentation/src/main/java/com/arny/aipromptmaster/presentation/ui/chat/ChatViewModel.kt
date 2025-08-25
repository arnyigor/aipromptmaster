package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ChatUiEvent {
    data class ShowError(val error: Throwable) : ChatUiEvent()
    data class ShareChat(val content: String) : ChatUiEvent()
}

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
    @Assisted private val conversationId: String?
) : ViewModel() {
    // Приватный изменяемый StateFlow для состояния загрузки
    private val _isLoading = MutableStateFlow(false)

    // Приватный SharedFlow для событий
    private val _uiEvents = MutableSharedFlow<ChatUiEvent>()
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    private val _currentConversationId = MutableStateFlow(conversationId)

    // ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ ДЛЯ UI
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = _currentConversationId.flatMapLatest { currentId ->
        // Если ID null, мы работаем с "пустыми" потоками
        val historyFlow = if (currentId != null) {
            interactor.getChatHistoryFlow(currentId)
        } else {
            flowOf(emptyList())
        }

        // НОВЫЙ ПОТОК: получаем системный промпт
        val systemPromptFlow = if (currentId != null) {
            // Мы можем создать flow, который эмитит значение из suspend функции
            flow<String?> { emit(interactor.getSystemPrompt(currentId)) }
        } else {
            flowOf(null) // Если ID нет, промпта тоже нет
        }

        // Усложняем combine, добавляя новый поток
        combine(
            historyFlow,
            interactor.getSelectedModel(),
            _isLoading,
            systemPromptFlow
        ) { messages, modelResult, isLoading, systemPrompt ->
            ChatUiState(
                messages = messages,
                selectedModel = (modelResult as? DataResult.Success)?.data,
                isLoading = isLoading,
                systemPrompt = systemPrompt
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
                    _uiEvents.emit(ChatUiEvent.ShowError(result.exception ?: DomainError.generic(R.string.no_model_selected)))
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

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversationId = _currentConversationId.value ?: run {
                    val newTitle = userMessageText.take(40).ifEmpty { "Новый чат" }
                    val newId = interactor.createNewConversation(newTitle)
                    _currentConversationId.value = newId
                    newId
                }

                interactor.addUserMessageToHistory(conversationId, userMessageText)

                val selectedModelId = uiState.value.selectedModel?.id
                    ?: throw DomainError.local(R.string.title_llm_interaction_model_not_selected)

                // Интерактор теперь пробрасывает ошибку (как мы делали в прошлый раз)
                interactor.sendMessageWithFallback(selectedModelId, conversationId)

            } catch (e: Exception) {
                // Ловим ошибку и превращаем ее в СОБЫТИЕ
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            } finally {
                // Гарантированно выключаем загрузку
                _isLoading.value = false
            }
        }
    }

    fun setSystemPrompt(prompt: String) {
        // ID диалога должен быть известен
        val currentId = _currentConversationId.value ?: return

        viewModelScope.launch {
            try {
                interactor.setSystemPrompt(currentId, prompt)
                // Можно отправить UiEvent об успехе, если нужно
            } catch (e: Exception) {
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
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
                _uiEvents.emit(ChatUiEvent.ShowError(e))
            }
        }
    }
}