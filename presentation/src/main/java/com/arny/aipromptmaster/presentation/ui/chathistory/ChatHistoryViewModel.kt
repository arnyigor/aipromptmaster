package com.arny.aipromptmaster.presentation.ui.chathistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID

// Добавляем Interactor в конструктор
class ChatHistoryViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatHistoryUIState>(ChatHistoryUIState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        // Запускаем сбор данных из Flow.
        // Он будет автоматически обновляться при любых изменениях в базе.
        viewModelScope.launch {
            interactor.getChatList()
                .onStart { _uiState.value = ChatHistoryUIState.Loading }
                .catch { e ->
                    _uiState.value = ChatHistoryUIState.Error(SimpleString(e.message ?: "Ошибка загрузки чатов"))
                }
                .collect { chats ->
                    _uiState.value = ChatHistoryUIState.Success(chats)
                }
        }
    }
}
