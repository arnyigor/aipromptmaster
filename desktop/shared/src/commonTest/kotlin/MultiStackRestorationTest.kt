import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arny.aiprompts.domain.model.*
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.presentation.navigation.*
import com.arny.aiprompts.presentation.screens.PromptDetailEvent
import com.arny.aiprompts.results.DataResult
import com.arny.promptcontract.ProviderConfig
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class MultiStackRestorationTest {
    @Test fun importFlagMatchesExplicitBuildType() {
        val expected = System.getProperty("expected.desktopBuildType") == "debug"
        assertEquals(expected, com.arny.aiprompts.BuildConfig.DEBUG)
        assertEquals(expected, MainComponent.IS_IMPORT_ENABLED)
    }
    @Test fun releaseRejectsDeveloperToolsAndRestoredImportTab() = runTest {
        if (MainComponent.IS_IMPORT_ENABLED) return@runTest
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry()
        try {
            val saved = StateKeeperDispatcher()
            saved.register("selected-tab", kotlinx.serialization.serializer<String>()) { "IMPORT" }
            saved.register("settings-return-tab", kotlinx.serialization.serializer<String>()) { "SCRAPER_WIZARD" }
            val root = createRoot(lifecycle, StateKeeperDispatcher(saved.save()))
            assertEquals(MainScreen.PROMPTS, root.state.value.currentScreen)
            root.navigateToImport(emptyList())
            root.navigateToScraperWizard()
            root.navigateToScraper()
            assertEquals(MainScreen.PROMPTS, root.state.value.currentScreen)
            assertIs<MainComponent.Child.Prompts>(root.stackFor(MainScreen.IMPORT).value.active.instance)
            assertIs<MainComponent.Child.Prompts>(root.stackFor(MainScreen.SCRAPER_WIZARD).value.active.instance)
            root.navigateToSettings()
            (root.childStack.value.active.instance as MainComponent.Child.Settings).component.onBackClicked()
            assertEquals(MainScreen.PROMPTS, root.state.value.currentScreen)
        } finally { lifecycle.destroy(); Dispatchers.resetMain() }
    }

    @Test fun settingsMenuClosesAndReturnsToThePreviousTab() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry()
        try {
            val root = createRoot(lifecycle, StateKeeperDispatcher())
            val prompts = (root.childStack.value.active.instance as MainComponent.Child.Prompts).component
            prompts.onMoreMenuToggle(true)
            prompts.onSettingsClicked()
            assertEquals(MainScreen.SETTINGS, root.state.value.currentScreen)
            assertFalse(prompts.state.value.isMoreMenuVisible)
            (root.childStack.value.active.instance as MainComponent.Child.Settings).component.onBackClicked()
            assertEquals(MainScreen.PROMPTS, root.state.value.currentScreen)
            assertSame(prompts, (root.childStack.value.active.instance as MainComponent.Child.Prompts).component)
            root.navigateToChat()
            val chat = root.childStack.value.active.instance
            root.navigateToSettings()
            (root.childStack.value.active.instance as MainComponent.Child.Settings).component.onBackClicked()
            assertEquals(MainScreen.CHAT, root.state.value.currentScreen)
            assertSame(chat, root.childStack.value.active.instance)
        } finally { lifecycle.destroy(); Dispatchers.resetMain() }
    }

    @Test fun tabsKeepTheirInstancesAndRestoreDraftAndChat() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val firstLifecycle = LifecycleRegistry()
        val nextLifecycle = LifecycleRegistry()
        try {
            val keeper = StateKeeperDispatcher()
            val root = createRoot(firstLifecycle, keeper)
            root.navigateToPromptDetails("fixture")
            advanceUntilIdle()
            val details = (root.childStack.value.active.instance as MainComponent.Child.PromptDetails).component
            details.onEvent(PromptDetailEvent.EditClicked)
            details.onEvent(PromptDetailEvent.TitleChanged("Unsaved title"))
            root.navigateToChat()
            val chat = (root.childStack.value.active.instance as MainComponent.Child.Chat).component
            chat.onPromptChanged("Unsaved chat input")
            root.navigateToSettings(); root.navigateToChat()
            assertSame(chat, (root.childStack.value.active.instance as MainComponent.Child.Chat).component)
            root.navigateToPrompts()
            assertSame(details, (root.childStack.value.active.instance as MainComponent.Child.PromptDetails).component)
            assertEquals("Unsaved title", details.state.value.draftPrompt?.title)
            root.navigateToChat()
            val recreated = createRoot(nextLifecycle, StateKeeperDispatcher(keeper.save()))
            advanceUntilIdle()
            assertEquals(MainScreen.CHAT, recreated.state.value.currentScreen)
            assertEquals("Unsaved chat input", (recreated.childStack.value.active.instance as MainComponent.Child.Chat).component.uiState.value.prompt)
            recreated.navigateToPrompts()
            assertEquals("Unsaved title", (recreated.childStack.value.active.instance as MainComponent.Child.PromptDetails).component.state.value.draftPrompt?.title)
        } finally { firstLifecycle.destroy(); nextLifecycle.destroy(); Dispatchers.resetMain() }
    }

    internal fun createRoot(lifecycle: LifecycleRegistry, keeper: StateKeeperDispatcher): DefaultMainComponent {
        val prompts = mockk<GetPromptsUseCase>(relaxed = true)
        every { prompts.getPromptsFlow() } returns flowOf(Result.success(emptyList()))
        val getPrompt = mockk<GetPromptUseCase>()
        coEvery { getPrompt.getPromptFlow(any()) } returns flowOf(Result.success(Prompt(id = "fixture", title = "Original", description = null, content = PromptContent("RU", "EN"), compatibleModels = emptyList(), category = "general", status = "active", createdAt = null, modifiedAt = null)))
        val tags = mockk<GetAvailableTagsUseCase>()
        coEvery { tags() } returns Result.success(emptyList())
        val importer = mockk<ImportJsonUseCase>()
        coEvery { importer() } returns Result.success(0)
        val synchronizer = mockk<com.arny.aiprompts.domain.repositories.IPromptSynchronizer>(relaxed = true)
        coEvery { synchronizer.synchronize(any()) } returns com.arny.aiprompts.domain.repositories.SyncResult.TooSoon
        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.loadProviders() } returns ProviderConfig()
        val llm = mockk<ILLMInteractor>(relaxed = true)
        every { llm.getModels() } returns flowOf(DataResult.Loading)
        every { llm.getAllSessions() } returns flowOf(emptyList())
        return DefaultMainComponent(
            componentContext = DefaultComponentContext(lifecycle, stateKeeper = keeper),
            getPromptsUseCase = prompts, getPromptUseCase = getPrompt,
            toggleFavoriteUseCase = mockk(relaxed = true), deletePromptUseCase = mockk(relaxed = true),
            deleteAllPromptsUseCase = mockk(relaxed = true), createPromptUseCase = mockk(relaxed = true),
            updatePromptUseCase = mockk(relaxed = true), scrapeUseCase = mockk(relaxed = true),
            webScraper = mockk(relaxed = true), processScrapedPostsUseCase = mockk(relaxed = true),
            getAvailableTagsUseCase = tags, importJsonUseCase = importer,
            parseRawPostsUseCase = mockk(relaxed = true), savePromptsAsFilesUseCase = mockk(relaxed = true),
            promptSynchronizer = synchronizer, promptsRepository = mockk(relaxed = true),
            hybridParser = mockk(relaxed = true), httpClient = mockk(relaxed = true),
            systemInteraction = mockk(relaxed = true), fileMetadataReader = mockk(relaxed = true),
            llmInteractor = llm, settingsRepository = settings, gitHubSyncService = mockk(relaxed = true),
            analyzerPipeline = mockk(relaxed = true), importParsedPromptsUseCase = mockk(relaxed = true),
            fileDataSource = mockk(relaxed = true), improvePromptUseCase = mockk(relaxed = true), providerProbe = mockk(relaxed = true)
        )
    }
}
