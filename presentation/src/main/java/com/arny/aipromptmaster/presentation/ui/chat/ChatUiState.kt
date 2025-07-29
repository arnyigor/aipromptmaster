package com.arny.aipromptmaster.presentation.ui.chat

import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val selectedModel: LlmModel? = null,
    val systemPrompt: String? = null
)