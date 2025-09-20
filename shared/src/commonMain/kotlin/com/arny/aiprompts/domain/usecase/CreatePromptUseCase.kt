package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.benasher44.uuid.uuid4
import kotlinx.datetime.Clock

/**
 * UseCase для создания нового промпта
 */
class CreatePromptUseCase(
    private val promptsRepository: IPromptsRepository
) {
    suspend operator fun invoke(
        title: String,
        contentRu: String = "",
        contentEn: String = "",
        description: String? = null,
        category: String = "",
        tags: List<String> = emptyList(),
        compatibleModels: List<String> = emptyList(),
        status: String = "active"
    ): Result<Long> {
        return runCatching {
            val now = Clock.System.now()
            val prompt = Prompt(
                id = uuid4().toString(), // Генерируем UUID для нового промпта
                title = title,
                content = com.arny.aiprompts.domain.model.PromptContent(
                    ru = contentRu,
                    en = contentEn
                ),
                description = description,
                category = category,
                tags = tags,
                compatibleModels = compatibleModels,
                status = status,
                isLocal = true, // Всегда true для локальных промптов
                createdAt = now,
                modifiedAt = now
            )
            promptsRepository.insertPrompt(prompt)
        }
    }
}