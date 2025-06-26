package com.arny.aipromptmaster.presentation.ui.home

sealed class UiAction {
    data class Search(
        val query: String = "",
        val category: String? = null,
        val status: String? = null,
        val tags: List<String> = emptyList()
    ) : UiAction()

    object Refresh : UiAction()
}