package com.arny.aiprompts.presentation.ui.importer

import java.util.Collections.emptyList

/**
 * Хранит отредактированные пользователем данные для одного поста.
 * Эта структура используется как значение в Map<PostId, EditedPostData>
 * внутри ImporterState.
 */
data class EditedPostData(
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val category: String = "imported",
    val tags: List<String> = emptyList()
)