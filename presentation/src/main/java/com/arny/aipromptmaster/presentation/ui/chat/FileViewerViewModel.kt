package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class FileViewerViewModel @AssistedInject constructor(
    private val fileRepository: IFileRepository,
    @Assisted private val fileId: String
) : ViewModel() {

    private val _fileAttachment = MutableStateFlow<FileAttachment?>(null)
    val fileAttachment = _fileAttachment.asStateFlow().filterNotNull()

    init {
        loadFile(fileId)
    }

    fun loadFile(fileId: String): Flow<FileAttachment?> {
        viewModelScope.launch {
            val file = fileRepository.getTemporaryFile(fileId)
            _fileAttachment.value = file
        }
        return fileAttachment
    }

    fun updateFileContent(fileId: String, newContent: String) {
        viewModelScope.launch {
            fileRepository.updateTemporaryFile(fileId, newContent)
        }
    }
}