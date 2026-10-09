package com.arny.aipromptmaster.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.ProviderGateway
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.promptcontract.ProviderManager

class ProvidersViewModel(settings: ISettingsRepository, gateway: ProviderGateway, local: com.arny.promptcontract.PersonalVaultLocal) : ViewModel() {
    val personalVault = com.arny.promptcontract.PersonalVaultManager(settings, local, com.arny.promptcontract.JvmGitHubPersonalVault(), viewModelScope)
    val manager = ProviderManager(settings, gateway, viewModelScope)
}
