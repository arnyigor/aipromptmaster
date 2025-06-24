package com.arny.aipromptmaster.presentation.ui.llm

sealed class LLMUIState {
    data class Content(
        val messages: List<String>,
        val isLoading: Boolean = false,
        val error: Throwable? = null
    ) : LLMUIState()

    companion object {
        val Initial = Content(emptyList())
    }
}