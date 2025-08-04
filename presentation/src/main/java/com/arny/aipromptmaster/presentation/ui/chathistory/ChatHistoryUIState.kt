package com.arny.aipromptmaster.presentation.ui.chathistory

import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.strings.StringHolder

sealed class ChatHistoryUIState {
    object Loading : ChatHistoryUIState()
    data class Success(val chats: List<Chat>) : ChatHistoryUIState()
    data class Error(val stringHolder: StringHolder?) : ChatHistoryUIState()
}