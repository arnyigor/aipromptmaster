package com.arny.aipromptmaster.presentation.ui.chathistory

sealed class ChatHistoryUIState {
    object Loading : ChatHistoryUIState()
    data class Success(val chats: List<Chat>) : ChatHistoryUIState()
    data class Error(val message: String) : ChatHistoryUIState()
}