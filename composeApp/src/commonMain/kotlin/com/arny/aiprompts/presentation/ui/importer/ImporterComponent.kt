package com.arny.aiprompts.presentation.ui.importer

import kotlinx.coroutines.flow.StateFlow

// Единица для действий с блоками
enum class BlockActionTarget { TITLE, DESCRIPTION, CONTENT }

interface ImporterComponent {
    val state: StateFlow<ImporterState>

    // --- События от списка постов ---
    fun onPostClicked(postId: String)
    fun onTogglePostForImport(postId: String, isChecked: Boolean)

    // --- События от панели редактирования ---
    fun onEditDataChanged(editedData: EditedPostData) // Универсальный метод
    fun onBlockActionClicked(text: String, target: BlockActionTarget) // Для кнопок "T, D, C"
    
    // --- События от кнопок управления ---
    fun onSkipPostClicked()
    fun onSaveAndSelectNextClicked()
    fun onImportClicked()
    fun onBackClicked()
    fun onVariantSelected(variant: PromptVariantData)
}