package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.repositories.SyncResult

class SyncPromptsUseCase(private val synchronizer: IPromptSynchronizer) {
    suspend operator fun invoke(ignoreCooldown: Boolean = false): SyncResult {
        return synchronizer.synchronize(ignoreCooldown)
    }
}