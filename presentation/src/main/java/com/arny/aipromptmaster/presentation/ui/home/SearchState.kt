package com.arny.aipromptmaster.presentation.ui.home

data class SearchState(
    val query: String = "",
    val category: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList()
)