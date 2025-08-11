package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.mappers.toPromptJson
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class SavePromptsAsFilesUseCase(
    private val fileDataSource: FileDataSource
) {
    suspend operator fun invoke(promptsData: List<PromptData>): Result<List<File>> {
        return runCatching {
            // Используем coroutineScope для параллельного сохранения
            coroutineScope {
                promptsData.map { promptData ->
                    async {
                        val promptJson = promptData.toPromptJson()
                        fileDataSource.savePromptJson(promptJson)
                    }
                }.awaitAll() // Ждем, пока все файлы сохранятся
            }
        }
    }
}