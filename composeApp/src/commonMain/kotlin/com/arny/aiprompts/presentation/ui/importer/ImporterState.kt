package com.arny.aiprompts.presentation.ui.importer

import com.arny.aiprompts.domain.model.RawPostData
import java.io.File

data class ImporterState(
    // Состояние процесса
    val isLoading: Boolean = false,
    val error: String? = null,

    // Данные
    val sourceHtmlFiles: List<File> = emptyList(),
    val rawPosts: List<RawPostData> = emptyList(),

    // Состояние UI
    val selectedPostId: String? = null,
    val postsToImport: Set<String> = emptySet(), // Set ID постов, отмеченных для импорта

    // Данные для редактирования выбранного поста
    val editableTitle: String = "",
    val editableDescription: String = "",
    val editableContent: String = ""
) {
    // Вычисляемое свойство для удобства получения выбранного поста
    val selectedPost: RawPostData?
        get() = rawPosts.find { it.postId == selectedPostId }
}