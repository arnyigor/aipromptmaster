package com.arny.aiprompts.presentation.ui.importer

import java.util.Collections

data class EditedPostData(
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val variants: List<PromptVariantData> = emptyList(),
    val category: String = "imported",
    val tags: List<String> = Collections.emptyList()
)

data class PromptVariantData(
    val title: String,
    val content: String
)