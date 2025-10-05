package com.arny.aipromptmaster.domain.repositories

import android.net.Uri
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import kotlinx.coroutines.flow.Flow

interface IFileRepository {
    suspend fun saveTemporaryFile(fileAttachment: FileAttachment): String
    suspend fun getTemporaryFile(id: String): FileAttachment?
    suspend fun updateTemporaryFile(id: String, updatedContent: String): Boolean
    suspend fun deleteTemporaryFile(id: String)
    fun getAllTemporaryFiles(): Flow<List<FileAttachment>>
      fun processFileFromUri(uri: Uri): Flow<FileProcessingResult>
}