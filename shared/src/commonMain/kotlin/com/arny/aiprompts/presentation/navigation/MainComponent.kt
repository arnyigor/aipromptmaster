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
import com.arny.aiprompts.data.model.getPlatform
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.CreatePromptUseCase
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import com.arny.aiprompts.domain.usecase.GetAvailableTagsUseCase
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ImportJsonUseCase
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
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
import com.arny.aiprompts.presentation.screens.PromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptListComponent
import com.arny.aiprompts.presentation.ui.importer.DefaultImporterComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterComponent
import com.arny.aiprompts.presentation.ui.scraper.DefaultScraperComponent
import com.arny.aiprompts.presentation.ui.scraper.ScraperComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface MainComponent {
    val state: StateFlow<MainState>
    val childStack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data class Scraper(val component: ScraperComponent) : Child
        data class Prompts(val component: PromptListComponent) : Child
        data class PromptDetails(val component: PromptDetailComponent) : Child
        data class Chat(val component: LlmComponent) : Child
        data class Import(val component: ImporterComponent) : Child
        data class Settings(val component: SettingsComponent) : Child
    }

    companion object {
        val IS_IMPORT_ENABLED: Boolean = getPlatform() == Platform.Desktop &&
                (System.getProperty("java.vm.name")?.contains("OpenJDK") == true ||
                        System.getenv("DEBUG_MODE") == "true" ||
                        try {
                            Class.forName("kotlinx.coroutines.debug.DebugProbes")
                            true
                        } catch (_: ClassNotFoundException) {
                            false
                        })
    }

    fun navigateToScraper()
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
    private val createPromptUseCase: CreatePromptUseCase,
    private val updatePromptUseCase: UpdatePromptUseCase,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: WebScraper,
    private val getAvailableTagsUseCase: GetAvailableTagsUseCase,
    private val importJsonUseCase: ImportJsonUseCase,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val hybridParser: IHybridParser,
    private val httpClient: HttpClient,
    private val systemInteraction: SystemInteraction,
    private val fileMetadataReader: FileMetadataReader,
    private val llmInteractor: ILLMInteractor,
) : MainComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<MainConfig>()
    private var importFiles = emptyList<File>()

    private val _state = MutableStateFlow(
        MainState(
            currentScreen = MainScreen.PROMPTS,
            sidebarCollapsed = false,
            activeWorkspace = null
        )
    )
    override val state: StateFlow<MainState> = _state.asStateFlow()

    override val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = MainConfig.serializer(),
            initialConfiguration = MainConfig.Prompts,
            handleBackButton = true,
            childFactory = ::createChild
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(config: MainConfig, context: ComponentContext): Child {
        return when (config) {

            is MainConfig.Prompts -> Child.Prompts(
                DefaultPromptListComponent(
                    componentContext = context,
                    getPromptsUseCase = getPromptsUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    importJsonUseCase = importJsonUseCase,
                    onNavigateToDetails = { promptId ->
                        navigation.push(MainConfig.PromptDetails(promptId))
                    },
                    onNavigateToScraper = {
                        this@DefaultMainComponent.navigateToScraper()
                    },
                    onNavigateToLLM = {
                        navigation.push(MainConfig.Chat)
                    },
                    deletePromptUseCase = deletePromptUseCase
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

            is MainConfig.Scraper -> Child.Scraper(
                DefaultScraperComponent(
                    componentContext = context,
                    scrapeUseCase = scrapeUseCase,
                    webScraper = webScraper,
                    parseRawPostsUseCase = parseRawPostsUseCase,
                    savePromptsAsFilesUseCase = savePromptsAsFilesUseCase,
                    onNavigateToImporter = { files ->
                        if (files.isNotEmpty()) {
                            navigation.push(MainConfig.Import)
                        }
                    },
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
                        _state.value = _state.value.copy(currentScreen = MainScreen.SCRAPER)
                    }
                )
            )

            is MainConfig.Settings -> Child.Settings(
                DefaultSettingsComponent(
                    componentContext = context
                )
            )
        }
    }

    override fun navigateToScraper() {
        navigation.navigate { listOf(MainConfig.Scraper) }
        _state.value = _state.value.copy(currentScreen = MainScreen.SCRAPER)
    }

    override fun navigateToPrompts() {
        navigation.navigate { listOf(MainConfig.Prompts) }
        _state.value = _state.value.copy(currentScreen = MainScreen.PROMPTS)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun navigateToPromptDetails(promptId: String) {
        navigation.push(MainConfig.PromptDetails(promptId))
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun navigateToChat() {
        navigation.push(MainConfig.Chat)
        _state.value = _state.value.copy(currentScreen = MainScreen.CHAT)
    }

    override fun navigateToImport(files: List<File>) {
        if (IS_IMPORT_ENABLED) {
            importFiles = files
            navigation.navigate { listOf(MainConfig.Import) }
            _state.value = _state.value.copy(currentScreen = MainScreen.IMPORT)
        } else {
            println("Import is only available in development mode")
        }
    }


    override fun navigateToSettings() {
        navigation.navigate { listOf(MainConfig.Settings) }
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

enum class MainScreen {
    SCRAPER,
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

// Settings component interface (to be implemented)
interface SettingsComponent {
    // Settings component methods will be defined here
}

class DefaultSettingsComponent(
    componentContext: ComponentContext
) : SettingsComponent, ComponentContext by componentContext {
    // Settings component implementation
}