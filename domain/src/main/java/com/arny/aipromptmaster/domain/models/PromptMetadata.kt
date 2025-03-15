package com.arny.aipromptmaster.domain.models

data class PromptMetadata(
    val author: Author = Author(),
    val source: String = "",
    val notes: String = ""
) 