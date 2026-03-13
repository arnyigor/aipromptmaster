package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.data.remote.GitHubSyncService
import com.arny.aiprompts.data.repositories.ISettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние подключения к GitHub.
 */
enum class ConnectionStatus {
    IDLE,
    CHECKING,
    SUCCESS,
    ERROR
}

/**
 * Состояние экрана настроек.
 */
data class SettingsState(
    // API Key
    val openRouterApiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    
    // Base URL (для LMStudio и др.)
    val baseUrl: String = "",
    
    // GitHub Sync
    val gitHubToken: String = "",
    val gitHubRepo: String = "",
    val isGitHubConnected: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    
    // User Context
    val userContext: String = "",
    
    // UI State
    val isLoading: Boolean = false,
    val saveMessage: String? = null,
    val activeSection: SettingsSection = SettingsSection.API
)

/**
 * Разделы настроек.
 */
enum class SettingsSection {
    API,
    GITHUB,
    PERSONALIZATION
}

/**
 * Интерфейс компонента настроек.
 */
interface SettingsComponent {
    val state: StateFlow<SettingsState>

    // Navigation
    fun onBackClicked()
    fun onSectionChanged(section: SettingsSection)
    
    // API Settings
    fun onOpenRouterApiKeyChanged(apiKey: String)
    fun onSaveOpenRouterApiKey()
    fun onToggleApiKeyVisibility()
    
    // Base URL
    fun onBaseUrlChanged(url: String)
    fun onSaveBaseUrl()
    
    // GitHub
    fun onGitHubTokenChanged(token: String)
    fun onGitHubRepoChanged(repo: String)
    fun onTestGitHubConnectionClicked()
    
    // User Context
    fun onUserContextChanged(context: String)
    fun onSaveUserContext()
}

/**
 * Реализация компонента настроек.
 */
class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val settingsRepository: ISettingsRepository,
    private val gitHubSyncService: GitHubSyncService,
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

            val currentApiKey = settingsRepository.getOpenRouterApiKey() ?: ""
            val baseUrl = settingsRepository.getBaseUrl() ?: ""
            val gitHubToken = settingsRepository.getGitHubToken() ?: ""
            val gitHubRepo = settingsRepository.getGitHubRepo() ?: ""
            val userContext = settingsRepository.getUserContext()

            _state.update {
                it.copy(
                    openRouterApiKey = currentApiKey,
                    baseUrl = baseUrl,
                    gitHubToken = gitHubToken,
                    gitHubRepo = gitHubRepo,
                    userContext = userContext,
                    isLoading = false
                )
            }
        }
    }

    override fun onSectionChanged(section: SettingsSection) {
        _state.update { it.copy(activeSection = section) }
    }

    // ==================== API Key ====================

    override fun onOpenRouterApiKeyChanged(apiKey: String) {
        _state.update {
            it.copy(
                openRouterApiKey = apiKey,
                saveMessage = null
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
                        saveMessage = "API ключ сохранен"
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

    // ==================== Base URL ====================

    override fun onBaseUrlChanged(url: String) {
        _state.update { it.copy(baseUrl = url, saveMessage = null) }
    }

    override fun onSaveBaseUrl() {
        scope.launch {
            _state.update { it.copy(isLoading = true, saveMessage = null) }
            
            try {
                settingsRepository.saveBaseUrl(_state.value.baseUrl)
                _state.update {
                    it.copy(
                        isLoading = false,
                        saveMessage = "Base URL сохранен"
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

    // ==================== GitHub ====================

    override fun onGitHubTokenChanged(token: String) {
        _state.update { 
            it.copy(
                gitHubToken = token, 
                connectionStatus = ConnectionStatus.IDLE,
                saveMessage = null
            ) 
        }
        settingsRepository.saveGitHubToken(token)
    }

    override fun onGitHubRepoChanged(repo: String) {
        _state.update { 
            it.copy(
                gitHubRepo = repo, 
                connectionStatus = ConnectionStatus.IDLE,
                saveMessage = null
            ) 
        }
        settingsRepository.saveGitHubRepo(repo)
    }

    override fun onTestGitHubConnectionClicked() {
        scope.launch {
            _state.update { it.copy(connectionStatus = ConnectionStatus.CHECKING, saveMessage = null) }
            
            val isSuccess = gitHubSyncService.checkConnection()
            
            _state.update { 
                it.copy(
                    connectionStatus = if (isSuccess) ConnectionStatus.SUCCESS else ConnectionStatus.ERROR,
                    isGitHubConnected = isSuccess,
                    saveMessage = if (isSuccess) "Подключение успешно" else "Ошибка подключения"
                ) 
            }
        }
    }

    // ==================== User Context ====================

    override fun onUserContextChanged(context: String) {
        _state.update { it.copy(userContext = context, saveMessage = null) }
    }

    override fun onSaveUserContext() {
        scope.launch {
            _state.update { it.copy(isLoading = true, saveMessage = null) }
            
            try {
                settingsRepository.saveUserContext(_state.value.userContext)
                _state.update {
                    it.copy(
                        isLoading = false,
                        saveMessage = "Персональный контекст сохранен"
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

    override fun onBackClicked() {
        onBack()
    }
}
