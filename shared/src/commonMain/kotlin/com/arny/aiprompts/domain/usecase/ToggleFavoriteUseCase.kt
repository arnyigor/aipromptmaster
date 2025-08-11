package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository

class ToggleFavoriteUseCase(private val repository: IPromptsRepository) {
    suspend operator fun invoke(promptId: String) {
        // Здесь можно добавить бизнес-логику, например, проверку прав
        repository.toggleFavoriteStatus(promptId)
    }
}