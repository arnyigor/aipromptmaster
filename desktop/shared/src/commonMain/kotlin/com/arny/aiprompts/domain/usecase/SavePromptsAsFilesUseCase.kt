package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.mappers.toPromptJson
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(kotlin.time.ExperimentalTime::class)
class SavePromptsAsFilesUseCase(
    private val fileDataSource: FileDataSource,
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(promptsData: List<PromptData>): Result<List<File>> {
        return try {
            // Используем coroutineScope для параллельного сохранения
            withContext(Dispatchers.IO) {
                val files = promptsData.map { promptData ->
                    async {
                        val promptJson = promptData.toPromptJson()
                        fileDataSource.savePromptJson(promptJson)
                    }
                }.awaitAll()
                // The library must update immediately, without requiring an app restart.
                promptsRepository.savePrompts(promptsData.map { it.toPromptJson().toDomain().copy(isLocal = true) })
                Result.success(files)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
