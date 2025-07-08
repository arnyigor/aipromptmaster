package com.arny.aipromptmaster.domain.models

data class PromptsSortData(
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)