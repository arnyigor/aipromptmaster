package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.repositories.IFeedbackRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import javax.inject.Inject

class SettingsInteractorImpl @Inject constructor(
    private val feedbackRepository: IFeedbackRepository,
    private val settingsRepository: ISettingsRepository
) : ISettingsInteractor {
    override fun saveApiKey(apiKey: String) {
        settingsRepository.saveApiKey(apiKey)
    }

    override suspend fun sendFeedback(content: String): Result<Unit> {
        return feedbackRepository.sendFeedback(content)
    }

    override fun getApiKey(): String? = settingsRepository.getApiKey()
}