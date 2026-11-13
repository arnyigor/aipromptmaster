package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository

/**
 * UseCase для удаления промпта
 */
class DeletePromptUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(promptId: String): Result<Unit> {
        return runCatching {
            promptsRepository.deletePrompt(promptId)
        }
    }
}