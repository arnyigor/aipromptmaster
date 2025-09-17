package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.errors.DomainError
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * UseCase для получения промпта по его id
 */
class GetPromptUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend fun getPromptFlow(promptId: String): Flow<Result<Prompt?>> {
        return flow {
            emit(promptsRepository.getPromptById(promptId).firstOrNull())
        }
            .map { prompt ->
                Result.success(prompt)
            }
            .catch { throwable ->
                val error = when (throwable) {
                    is DomainError -> throwable
                    else -> DomainError.Local("An unexpected error occurred in the data flow.")
                }
                emit(Result.failure(error))
            }
    }
}
