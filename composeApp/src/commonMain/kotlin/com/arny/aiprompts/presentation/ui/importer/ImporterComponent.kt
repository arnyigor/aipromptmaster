package com.arny.aiprompts.presentation.ui.importer

import kotlinx.coroutines.flow.StateFlow

interface ImporterComponent {
    val state: StateFlow<ImporterState>

    // События от UI
    fun onPostClicked(postId: String)
    fun onTogglePostForImport(postId: String, isChecked: Boolean)
    fun onTitleChanged(newTitle: String)
    fun onDescriptionChanged(newDescription: String)
    fun onContentChanged(newContent: String)
    fun onImportClicked()
    fun onBackClicked()
}