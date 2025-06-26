package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import javax.inject.Inject

class SettingsInteractorImpl @Inject constructor(
    private val settingsRepository: ISettingsRepository
) : ISettingsInteractor {
    override fun saveApiKey(apiKey: String) {
        settingsRepository.saveApiKey(apiKey)
    }

    override fun getApiKey(): String? {
        return settingsRepository.getApiKey()
    }
}