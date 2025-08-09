package com.arny.aiprompts.domain.model

import com.arny.aiprompts.domain.model.Author

data class PromptMetadata(
    val author: Author = Author(),
    val source: String = "",
    val notes: String = ""
)