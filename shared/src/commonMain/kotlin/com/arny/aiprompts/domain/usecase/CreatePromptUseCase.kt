package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
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
        compatibleModels: List<String> = emptyList()
    ): Result<Long> {
        return runCatching {
            val now = Clock.System.now()
            val prompt = Prompt(
                id = "", // Пустой ID для генерации нового
                title = title,
                content = com.arny.aiprompts.domain.model.PromptContent(
                    ru = contentRu,
                    en = contentEn
                ),
                description = description,
                category = category,
                tags = tags,
                compatibleModels = compatibleModels,
                status = "active",
                isLocal = true, // Обязательный важный параметр для локальных промптов
                createdAt = now,
                modifiedAt = now
            )
            promptsRepository.insertPrompt(prompt)
        }
    }
}