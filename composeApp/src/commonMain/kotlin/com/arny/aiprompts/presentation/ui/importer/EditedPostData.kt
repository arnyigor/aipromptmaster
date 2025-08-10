package com.arny.aiprompts.presentation.ui.importer

import java.util.Collections

/**
 * Хранит отредактированные пользователем данные для одного поста.
 * Эта структура используется как значение в Map<PostId, ExtractedPromptData>
 * внутри ImporterState.
 */
data class ExtractedPromptData(
    val title: String,
    val description: String,
    val content: String,
    val category: String = "imported",
    val tags: List<String> = Collections.emptyList()
)