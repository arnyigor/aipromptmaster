package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository

/**
 * UseCase для удаления всех промптов
 */
class DeleteAllPromptsUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return runCatching {
            promptsRepository.deleteAllPrompts()
        }
    }
}