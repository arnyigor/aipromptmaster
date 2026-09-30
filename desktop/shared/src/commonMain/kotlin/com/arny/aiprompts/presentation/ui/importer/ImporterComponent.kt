package com.arny.aiprompts.presentation.ui.importer

import kotlinx.coroutines.flow.StateFlow

// Единица для действий с блоками
enum class BlockActionTarget { TITLE, DESCRIPTION, CONTENT }

interface ImporterComponent {
    val state: StateFlow<ImporterState>

    // --- Навигация и выбор ---
    fun onPostClicked(postId: String)
    fun onTogglePostForImport(postId: String, isChecked: Boolean)
    fun onBackClicked()

    // --- Фильтры и поиск ---
    fun onSearchQueryChanged(query: String)
    fun onFilterChanged(filter: PostFilters)
    fun onGroupingChanged(grouping: PostGrouping)

    // --- Редактирование ---
    fun onEditDataChanged(editedData: EditedPostData)
    fun onBlockActionClicked(text: String, target: BlockActionTarget)
    fun onVariantSelected(variant: PromptVariantData)

    // --- Управление процессом ---
    fun onSkipPostClicked()
    fun onSaveAndSelectNextClicked()
    fun onSaveAndSelectPreviousClicked()
    fun onSelectNextPost()
    fun onSelectPreviousPost()
    fun onImportClicked()

    // --- UI состояние ---
    fun onTogglePreview()
    fun onTogglePostExpansion(postId: String)
    fun onDismissError()
    fun onDismissSuccess()

    // --- Работа с файлами ---
    fun onDownloadFile(attachmentUrl: String, filename: String)
    fun onPreviewFile(attachmentUrl: String, filename: String)
    fun onOpenFileInSystem(attachmentUrl: String, filename: String)
    fun onToggleFileExpansion(postId: String, attachmentUrl: String)
    fun onOpenPostInBrowser(postUrl: String)

    // --- Валидация ---
    fun validateEditedData(postId: String): Boolean
    fun getValidationErrors(postId: String): Map<String, Map<String, String>>
}