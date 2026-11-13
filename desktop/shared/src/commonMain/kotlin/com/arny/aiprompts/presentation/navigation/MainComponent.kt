package com.arny.aiprompts.presentation.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arny.aiprompts.data.model.Platform
import com.arny.aiprompts.getPlatform
import com.arny.aiprompts.data.remote.GitHubSyncService
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.domain.analysis.IAnalyzerPipeline
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.CreatePromptUseCase
import com.arny.aiprompts.domain.usecase.DeleteAllPromptsUseCase
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import com.arny.aiprompts.domain.usecase.GetAvailableTagsUseCase
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ImportJsonUseCase
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.ProcessScrapedPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import com.arny.aiprompts.domain.usecase.UpdatePromptUseCase
import com.arny.aiprompts.presentation.features.llm.DefaultLlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.navigation.MainComponent.Child
import com.arny.aiprompts.presentation.navigation.MainComponent.Companion.IS_IMPORT_ENABLED
import com.arny.aiprompts.presentation.screens.DefaultPromptDetailComponent
import com.arny.aiprompts.presentation.screens.DefaultPromptListComponent
import com.arny.aiprompts.presentation.screens.DefaultSettingsComponent
import com.arny.aiprompts.presentation.screens.PromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptListComponent
import com.arny.aiprompts.presentation.screens.SettingsComponent
import com.arny.aiprompts.presentation.ui.importer.DefaultImporterComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterComponent
import com.arny.aiprompts.presentation.ui.scraperwizard.DefaultScraperWizardComponent
import com.arny.aiprompts.presentation.ui.scraperwizard.ScraperWizardComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface MainComponent {
    val state: StateFlow<MainState>
    val childStack: Value<ChildStack<*, Child>>
    fun stackFor(screen: MainScreen): Value<ChildStack<*, Child>> = childStack

    sealed interface Child {
        data class ScraperWizard(val component: ScraperWizardComponent) : Child
        data class Prompts(val component: PromptListComponent) : Child
        data class PromptDetails(val component: PromptDetailComponent) : Child
        data class Chat(val component: LlmComponent) : Child
        data class Import(val component: ImporterComponent) : Child
        data class Settings(val component: SettingsComponent) : Child
    }

    companion object {
        val IS_IMPORT_ENABLED: Boolean = getPlatform() == Platform.Desktop && com.arny.aiprompts.BuildConfig.IS_IMPORT_ENABLED
    }

    fun navigateToScraper()
    fun navigateToScraperWizard()
    fun navigateToPrompts()
    fun navigateToPromptDetails(promptId: String)
    fun navigateToChat()
    fun navigateToImport(files: List<File> = emptyList())
    fun navigateToSettings()
    fun toggleSidebar()
    fun togglePropertiesPanel()
    fun selectWorkspace(workspaceId: String)
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val getPromptsUseCase: GetPromptsUseCase,
    private val getPromptUseCase: GetPromptUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deletePromptUseCase: DeletePromptUseCase,
    private val deleteAllPromptsUseCase: DeleteAllPromptsUseCase,
    private val createPromptUseCase: CreatePromptUseCase,
    private val updatePromptUseCase: UpdatePromptUseCase,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: IWebScraper,
    private val processScrapedPostsUseCase: ProcessScrapedPostsUseCase,
    private val getAvailableTagsUseCase: GetAvailableTagsUseCase,
    private val importJsonUseCase: ImportJsonUseCase,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val promptSynchronizer: IPromptSynchronizer,
    private val promptsRepository: IPromptsRepository,
    private val hybridParser: IHybridParser,
    private val httpClient: HttpClient,
    private val systemInteraction: SystemInteraction,
    private val fileMetadataReader: FileMetadataReader,
    private val llmInteractor: ILLMInteractor,
    private val settingsRepository: ISettingsRepository,
    private val gitHubSyncService: GitHubSyncService,
    private val analyzerPipeline: IAnalyzerPipeline,
    private val importParsedPromptsUseCase: ImportParsedPromptsUseCase,
    private val fileDataSource: FileDataSource,
    private val improvePromptUseCase: com.arny.aiprompts.domain.usecase.ImprovePromptUseCase,
    private val providerProbe: com.arny.promptcontract.ProviderProbe,
) : MainComponent, ComponentContext by componentContext {

    private val tabNavigations = MainScreen.entries.associateWith { StackNavigation<MainConfig>() }
    private val navigation: StackNavigation<MainConfig> get() {
        stackForScreen(_state.value.currentScreen)
        return tabNavigations.getValue(_state.value.currentScreen)
    }
    private var importFiles = emptyList<File>()
    private var settingsReturnScreen = stateKeeper.consume("settings-return-tab", kotlinx.serialization.serializer<String>())
        ?.let { name -> MainScreen.entries.firstOrNull { it.name == name && it != MainScreen.SETTINGS }?.let(::allowedMainScreen) }
        ?: MainScreen.PROMPTS

    private val _state = MutableStateFlow(
        MainState(
            currentScreen = stateKeeper.consume("selected-tab", kotlinx.serialization.serializer<String>())
                ?.let { name -> MainScreen.entries.firstOrNull { it.name == name }?.let(::allowedMainScreen) } ?: MainScreen.PROMPTS,
            sidebarCollapsed = false,
            activeWorkspace = null
        )
    )
    override val state: StateFlow<MainState> = _state.asStateFlow()

    private val tabStacks = mutableMapOf<MainScreen, Value<ChildStack<MainConfig, Child>>>()
    private fun stackForScreen(tab: MainScreen): Value<ChildStack<MainConfig, Child>> = tabStacks.getOrPut(tab) {
        childStack(
            source = tabNavigations.getValue(tab),
            serializer = MainConfig.serializer(),
            initialConfiguration = when (tab) {
                MainScreen.PROMPTS -> MainConfig.Prompts
                MainScreen.CHAT -> MainConfig.Chat
                MainScreen.SCRAPER_WIZARD -> MainConfig.ScraperWizard
                MainScreen.IMPORT -> MainConfig.Import
                MainScreen.SETTINGS -> MainConfig.Settings
            },
            key = "tab-${tab.name}",
            handleBackButton = false,
            childFactory = ::createChild
        )
    }
    override val childStack: Value<ChildStack<*, Child>> get() = stackForScreen(_state.value.currentScreen)
    override fun stackFor(screen: MainScreen): Value<ChildStack<*, Child>> = stackForScreen(allowedMainScreen(screen))

    init {
        stateKeeper.register("selected-tab", kotlinx.serialization.serializer<String>()) { _state.value.currentScreen.name }
        stateKeeper.register("settings-return-tab", kotlinx.serialization.serializer<String>()) { settingsReturnScreen.name }
    }

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(config: MainConfig, context: ComponentContext): Child {
        if (!IS_IMPORT_ENABLED && (config is MainConfig.Import || config is MainConfig.ScraperWizard)) {
            return createChild(MainConfig.Prompts, context)
        }
        return when (config) {

            is MainConfig.Prompts -> Child.Prompts(
                DefaultPromptListComponent(
                    componentContext = context,
                    getPromptsUseCase = getPromptsUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    importJsonUseCase = importJsonUseCase,
                    deletePromptUseCase = deletePromptUseCase,
                    deleteAllPromptsUseCase = deleteAllPromptsUseCase,
                    promptSynchronizer = promptSynchronizer,
                    onNavigateToDetails = { promptId ->
                        navigation.push(MainConfig.PromptDetails(promptId))
                    },
                    onNavigateToScraper = {
                        this@DefaultMainComponent.navigateToScraper()
                    },
                    onNavigateToLLM = {
                        navigateToChat()
                    },
                    onNavigateToSettings = ::navigateToSettings,
                )
            )

            is MainConfig.PromptDetails -> Child.PromptDetails(
                DefaultPromptDetailComponent(
                    componentContext = context,
                    getPromptUseCase = getPromptUseCase,
                    updatePromptUseCase = updatePromptUseCase,
                    createPromptUseCase = createPromptUseCase,
                    deletePromptUseCase = deletePromptUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    getAvailableTagsUseCase = getAvailableTagsUseCase,
                    promptId = config.promptId,
                    improvePromptUseCase = improvePromptUseCase,
                    onNavigateBack = { navigation.pop() }
                )
            )

            is MainConfig.Chat -> Child.Chat(
                DefaultLlmComponent(
                    componentContext = context,
                    llmInteractor = llmInteractor,
                    onBack = { navigation.pop() }
                )
            )

            is MainConfig.ScraperWizard -> Child.ScraperWizard(
                DefaultScraperWizardComponent(
                    componentContext = context,
                    scrapeUseCase = scrapeUseCase,
                    webScraper = webScraper,
                    promptSynchronizer = promptSynchronizer,
                    promptsRepository = promptsRepository,
                    fileDataSource = fileDataSource,
                    onBack = { navigation.pop() }
                )
            )

            is MainConfig.Import -> Child.Import(
                DefaultImporterComponent(
                    componentContext = context,
                    filesToImport = importFiles,
                    parseRawPostsUseCase = parseRawPostsUseCase,
                    savePromptsAsFilesUseCase = savePromptsAsFilesUseCase,
                    hybridParser = hybridParser,
                    httpClient = httpClient,
                    systemInteraction = systemInteraction,
                    fileMetadataReader = fileMetadataReader,
                    onBack = {
                        navigation.pop()
                        _state.value = _state.value.copy(currentScreen = MainScreen.PROMPTS)
                    }
                )
            )

            is MainConfig.Settings -> Child.Settings(
                DefaultSettingsComponent(
                    componentContext = context,
                    settingsRepository = settingsRepository,
                    gitHubSyncService = gitHubSyncService,
                    probe = providerProbe,
                    onBack = { _state.value = _state.value.copy(currentScreen = settingsReturnScreen) }
                )
            )
        }
    }

    override fun navigateToScraper() {
        // Redirect to ScraperWizard (single scraper entry point)
        navigateToScraperWizard()
    }

    override fun navigateToScraperWizard() {
        if (!IS_IMPORT_ENABLED) return
        _state.value = _state.value.copy(currentScreen = MainScreen.SCRAPER_WIZARD)
    }

    override fun navigateToPrompts() {
        _state.value = _state.value.copy(currentScreen = MainScreen.PROMPTS)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun navigateToPromptDetails(promptId: String) {
        navigation.push(MainConfig.PromptDetails(promptId))
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun navigateToChat() {
        _state.value = _state.value.copy(currentScreen = MainScreen.CHAT)
    }

    override fun navigateToImport(files: List<File>) {
        if (IS_IMPORT_ENABLED) {
            importFiles = files
            _state.value = _state.value.copy(currentScreen = MainScreen.IMPORT)
            if (files.isNotEmpty()) (stackFor(MainScreen.IMPORT).value.active.instance as? Child.Import)?.component?.onLoadFiles(files)
        } else {
            println("Import is only available in development mode")
        }
    }

    override fun navigateToSettings() {
        if (_state.value.currentScreen != MainScreen.SETTINGS) settingsReturnScreen = _state.value.currentScreen
        _state.value = _state.value.copy(currentScreen = MainScreen.SETTINGS)
    }

    override fun toggleSidebar() {
        _state.value = _state.value.copy(
            sidebarCollapsed = !_state.value.sidebarCollapsed
        )
    }

    override fun togglePropertiesPanel() {
        _state.value = _state.value.copy(
            propertiesPanelCollapsed = !_state.value.propertiesPanelCollapsed
        )
    }

    override fun selectWorkspace(workspaceId: String) {
        // TODO: Implement workspace selection logic
        _state.value = _state.value.copy(
            activeWorkspace = Workspace(
                id = workspaceId,
                name = "Workspace $workspaceId",
                settings = WorkspaceSettings()
            )
        )
    }
}

// State and data classes
data class MainState(
    val currentScreen: MainScreen = MainScreen.PROMPTS,
    val sidebarCollapsed: Boolean = false,
    val propertiesPanelCollapsed: Boolean = true,
    val showImportDialog: Boolean = false,
    val activeWorkspace: Workspace? = null
)

internal fun allowedMainScreen(screen: MainScreen): MainScreen =
    if (!MainComponent.IS_IMPORT_ENABLED && screen in setOf(MainScreen.IMPORT, MainScreen.SCRAPER_WIZARD)) MainScreen.PROMPTS else screen

enum class MainScreen {
    SCRAPER_WIZARD,
    PROMPTS,
    CHAT,
    IMPORT,
    SETTINGS
}

data class Workspace(
    val id: String,
    val name: String,
    val activePrompts: List<String> = emptyList(),
    val activeChat: String? = null,
    val importSession: String? = null,
    val settings: WorkspaceSettings = WorkspaceSettings()
)

data class WorkspaceSettings(
    val theme: String = "system",
    val language: String = "en",
    val autoSave: Boolean = true
)
