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

    // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
    // Хранилище для всех отредактированных "черновиков".
    // Ключ - postId, Значение - отредактированные данные.
    val editedData: Map<String, ExtractedPromptData> = emptyMap(),

    val selectedText: String = "" // Новое поле для хранения выделенного текста
) {
    // Вычисляемое свойство для удобства получения выбранного поста
    val selectedPost: RawPostData?
        get() = rawPosts.find { it.postId == selectedPostId }

    // Новое вычисляемое свойство для удобного доступа к черновику ВЫБРАННОГО поста
    val currentEditedData: ExtractedPromptData?
        get() = selectedPostId?.let { editedData[it] }
}