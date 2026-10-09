package com.arny.aipromptmaster.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.ProviderGateway
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.promptcontract.ProviderManager

class ProvidersViewModel(settings: ISettingsRepository, gateway: ProviderGateway, local: com.arny.promptcontract.PersonalVaultLocal,
                         private val savedState: SavedStateHandle) : ViewModel() {
    val section = savedState.getStateFlow("settings-section", "API")
    fun onSection(id: String) {
        if (id in setOf("API", "GITHUB", "FEEDBACK")) savedState["settings-section"] = id
    }
    val personalVault = com.arny.promptcontract.PersonalVaultManager(settings, local, com.arny.promptcontract.JvmGitHubPersonalVault(), viewModelScope)
    val manager = ProviderManager(settings, gateway, viewModelScope)
}
