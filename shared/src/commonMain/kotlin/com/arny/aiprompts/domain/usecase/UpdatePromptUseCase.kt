package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import kotlinx.datetime.Clock

/**
 * UseCase для обновления существующего промпта
 */
class UpdatePromptUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(
        promptId: String,
        title: String? = null,
        contentRu: String? = null,
        contentEn: String? = null,
        description: String? = null,
        category: String? = null,
        tags: List<String>? = null,
        compatibleModels: List<String>? = null
    ): Result<Unit> {
        return runCatching {
            // Получаем существующий промпт
            val existingPrompt = promptsRepository.getPromptById(promptId)
                ?: throw IllegalArgumentException("Prompt with id $promptId not found")

            // Создаем обновленный промпт, применяя только непустые изменения
            val updatedPrompt = existingPrompt.copy(
                title = title ?: existingPrompt.title,
                content = existingPrompt.content?.copy(
                    ru = contentRu ?: existingPrompt.content?.ru.orEmpty(),
                    en = contentEn ?: existingPrompt.content?.en.orEmpty()
                ),
                isLocal = existingPrompt.isLocal,
                description = description ?: existingPrompt.description,
                category = category ?: existingPrompt.category,
                tags = tags ?: existingPrompt.tags,
                compatibleModels = compatibleModels ?: existingPrompt.compatibleModels,
                modifiedAt = Clock.System.now()
            )

            promptsRepository.updatePrompt(updatedPrompt)
        }
    }
}