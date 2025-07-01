package com.arny.aipromptmaster.presentation.ui.chathistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatHistoryViewModel @AssistedInject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ChatHistoryUIState>(ChatHistoryUIState.Loading)
    val uiState = _uiState.asStateFlow()

    fun loadChats() {
        viewModelScope.launch {
            try {
                _uiState.value = ChatHistoryUIState.Loading
                delay(1000)
                _uiState.value = ChatHistoryUIState.Success(emptyList())
            } catch (e: Exception) {
                _uiState.value =
                    ChatHistoryUIState.Error(SimpleString(e.message ?: "Ошибка загрузки чатов"))
            }
        }
    }
}