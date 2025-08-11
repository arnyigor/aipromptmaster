package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.PromptJson
import java.io.File

// Интерфейс для работы с файлами
interface FileDataSource {
    fun getParsedPromptsDirectory(): File
    suspend fun savePromptJson(promptJson: PromptJson): File

    suspend fun getPromptFiles(): List<PlatformFile>
}