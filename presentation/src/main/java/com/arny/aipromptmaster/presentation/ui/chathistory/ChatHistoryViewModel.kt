package com.arny.aipromptmaster.presentation.ui.chathistory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatHistoryViewModel @AssistedInject constructor(
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatHistoryUIState>(ChatHistoryUIState.Loading)
    val uiState = _uiState.asStateFlow()

    fun loadChats() {
        viewModelScope.launch {
            try {
                Log.i("ChatHistoryViewModel", "loadChats: start load data")
                _uiState.value = ChatHistoryUIState.Loading
                // Здесь должна быть реальная загрузка данных
                val testChats = listOf(
                    Chat(
                        id = UUID.randomUUID().toString(),
                        name = "Техподдержка",
                        lastMessage = "Как я могу вам помочь?",
                        timestamp = System.currentTimeMillis() - 3600000
                    ),
                    Chat(
                        id = UUID.randomUUID().toString(),
                        name = "Код ревью",
                        lastMessage = "Здесь нужно улучшить производительность...",
                        timestamp = System.currentTimeMillis() - 7200000
                    )
                )
                _uiState.value = ChatHistoryUIState.Success(emptyList())
            } catch (e: Exception) {
                _uiState.value = ChatHistoryUIState.Error(e.message ?: "Ошибка загрузки чатов")
            }
        }
    }
}