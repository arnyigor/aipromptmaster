package com.arny.aipromptmaster.presentation.ui.chat

sealed class ChatUIState {
    data class Content(
        val messages: List<AiChatMessage>,
        val isLoading: Boolean = false,
        val error: Throwable? = null
    ) : ChatUIState()

    companion object {
        val Initial = Content(emptyList())
    }
}