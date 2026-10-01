package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository

/**
 * UseCase для получения списка всех доступных тегов для автодополнения
 */
class GetAvailableTagsUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(): Result<List<String>> {
        return runCatching {
            // Получаем уникальные теги напрямую из базы данных
            promptsRepository.getAllUniqueTags()
        }
    }
}