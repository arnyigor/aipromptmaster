package com.arny.aiprompts.presentation.ui.importer

import kotlinx.coroutines.flow.StateFlow

interface ImporterComponent {
    val state: StateFlow<ImporterState>

    // События от UI
    fun onPostClicked(postId: String)
    fun onTogglePostForImport(postId: String, isChecked: Boolean)

    // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
    // Универсальный метод для обновления данных в полях редактирования
    fun onEditDataChanged(editedData: ExtractedPromptData)

    fun onImportClicked()
    fun onBackClicked()

    // Новые методы для кнопок управления постом
    fun onSkipPostClicked()
    fun onSaveAndSelectNextClicked()
}

enum class AssignTarget { CONTENT, DESCRIPTION }
