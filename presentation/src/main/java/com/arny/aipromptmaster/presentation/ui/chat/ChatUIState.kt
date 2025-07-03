package com.arny.aipromptmaster.presentation.ui.chat

import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    val selectedModel: LlmModel? = null
)