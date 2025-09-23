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
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ImportJsonUseCase
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import com.arny.aiprompts.presentation.features.llm.DefaultLlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.navigation.MainComponent.Child
import com.arny.aiprompts.presentation.navigation.MainComponent.Companion.IS_IMPORT_ENABLED
import com.arny.aiprompts.presentation.screens.DefaultPromptListComponent
import com.arny.aiprompts.presentation.screens.PromptListComponent
import com.arny.aiprompts.presentation.ui.importer.DefaultImporterComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MainComponent {
    val state: StateFlow<MainState>
    val childStack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data class Prompts(val component: PromptListComponent) : Child
        data class Chat(val component: LlmComponent) : Child
        data class Import(val component: ImporterComponent) : Child
        data class Settings(val component: SettingsComponent) : Child
    }

    companion object {
        // Import доступен только на Desktop в режиме разработки
        val IS_IMPORT_ENABLED: Boolean = getPlatform() == Platform.Desktop &&
                (System.getProperty("java.vm.name")?.contains("OpenJDK") == true ||
                        System.getenv("DEBUG_MODE") == "true" ||
                        try {
                            // Попытка загрузки debug-only класса
                            Class.forName("kotlinx.coroutines.debug.DebugProbes")
                            true
                        } catch (_: ClassNotFoundException) {
                            false
                        })
    }

    fun navigateToPrompts()
    fun navigateToChat()
    fun navigateToImport(files: List<java.io.File> = emptyList())
    fun navigateToSettings()
    fun toggleSidebar()
    fun selectWorkspace(workspaceId: String)
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val getPromptsUseCase: GetPromptsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val deletePromptUseCase: DeletePromptUseCase,
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
    private var importFiles = emptyList<java.io.File>()

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
                        // Handle prompt details navigation - for now just log
                        println("Navigate to prompt details: $promptId")
                    },
                    onNavigateToScraper = {
                        this@DefaultMainComponent.navigateToImport(emptyList())
                    },
                    onNavigateToLLM = {
                        navigation.push(MainConfig.Chat)
                    },
                    deletePromptUseCase = deletePromptUseCase
                )
            )

            is MainConfig.Chat -> Child.Chat(
                DefaultLlmComponent(
                    componentContext = context,
                    llmInteractor = llmInteractor,
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
                    componentContext = context
                )
            )
        }
    }

    override fun navigateToPrompts() {
        navigation.navigate { stack ->
            stack.dropLast(1) + MainConfig.Prompts
        }
        _state.value = _state.value.copy(currentScreen = MainScreen.PROMPTS)
    }

    override fun navigateToChat() {
        navigation.navigate { stack ->
            stack.dropLast(1) + MainConfig.Chat
        }
        _state.value = _state.value.copy(currentScreen = MainScreen.CHAT)
    }

    override fun navigateToImport(files: List<java.io.File>) {
        if (IS_IMPORT_ENABLED) {
            importFiles = files
            navigation.navigate { stack ->
                stack.dropLast(1) + MainConfig.Import
            }
            _state.value = _state.value.copy(currentScreen = MainScreen.IMPORT)
        } else {
            println("Import is only available in development mode")
        }
    }

    override fun navigateToSettings() {
        navigation.navigate { stack ->
            stack.dropLast(1) + MainConfig.Settings
        }
        _state.value = _state.value.copy(currentScreen = MainScreen.SETTINGS)
    }

    override fun toggleSidebar() {
        _state.value = _state.value.copy(
            sidebarCollapsed = !_state.value.sidebarCollapsed
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
    val showImportDialog: Boolean = false,
    val activeWorkspace: Workspace? = null
)

enum class MainScreen {
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