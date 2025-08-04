package com.arny.aipromptmaster.presentation.ui.chathistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.models.strings.toErrorHolder
import com.arny.aipromptmaster.presentation.R
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

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
                    _uiState.value = ChatHistoryUIState.Error(StringHolder.Text(e.message))
                }
                .collect { chats ->
                    _uiState.value = ChatHistoryUIState.Success(chats)
                }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                interactor.deleteConversation(conversationId)
                // Ничего больше делать не нужно! Flow сам обновит список.
            } catch (e: Exception) {
                _uiState.value = ChatHistoryUIState.Error(e.toErrorHolder(R.string.remove_error))
            }
        }
    }
}
