package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.data.repositories.ISettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val openRouterApiKey: String = "",
    val isLoading: Boolean = false,
    val saveMessage: String? = null,
    val isApiKeyVisible: Boolean = false
)

interface SettingsComponent {
    val state: StateFlow<SettingsState>

    fun onOpenRouterApiKeyChanged(apiKey: String)
    fun onSaveOpenRouterApiKey()
    fun onToggleApiKeyVisibility()
    fun onBackClicked()
}

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val settingsRepository: ISettingsRepository,
    private val onBack: () -> Unit
) : SettingsComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val scope = coroutineScope()

    init {
        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }

            val currentApiKey = settingsRepository.getOpenRouterApiKey()

            _state.update {
                it.copy(
                    openRouterApiKey = currentApiKey ?: "",
                    isLoading = false
                )
            }
        }
    }

    override fun onOpenRouterApiKeyChanged(apiKey: String) {
        _state.update {
            it.copy(
                openRouterApiKey = apiKey,
                saveMessage = null // Сбрасываем сообщение при изменении
            )
        }
    }

    override fun onSaveOpenRouterApiKey() {
        scope.launch {
            _state.update { it.copy(isLoading = true, saveMessage = null) }

            try {
                settingsRepository.saveOpenRouterApiKey(_state.value.openRouterApiKey)

                _state.update {
                    it.copy(
                        isLoading = false,
                        saveMessage = "Настройки сохранены успешно"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        saveMessage = "Ошибка сохранения: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onToggleApiKeyVisibility() {
        _state.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    override fun onBackClicked() {
        onBack()
    }
}