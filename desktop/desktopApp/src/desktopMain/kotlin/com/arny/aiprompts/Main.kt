package com.arny.aiprompts

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arny.aiprompts.data.di.desktopModules
import com.arny.aiprompts.di.commonModules
import com.arny.aiprompts.presentation.navigation.DefaultMainComponent
import com.arny.aiprompts.presentation.ui.MainContentDesktopImpl
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    startKoin {
        modules(commonModules + desktopModules)
    }

    // Запуск синхронизации промптов в фоне
    CoroutineScope(Dispatchers.IO).launch {
        val synchronizer = getKoin().get<IPromptSynchronizer>()
        
        // Сначала загружаем локальные промпты (созданные через Importer)
        println("✅ [main] Загрузка локальных промптов...")
        synchronizer.loadLocalPrompts()
        
        // Затем синхронизируем с GitHub
        println("✅ [main] Синхронизация с GitHub...")
        synchronizer.synchronize()
    }

    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Maximized,
            position = WindowPosition(Alignment.Center),
        )
        val lifecycle = remember { LifecycleRegistry() }
        val sessionStore = remember { com.arny.aiprompts.platform.DesktopSessionStore() }
        val stateKeeper = remember { com.arkivanov.essenty.statekeeper.StateKeeperDispatcher(sessionStore.load()) }

        val root = remember {
            DefaultMainComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle, stateKeeper = stateKeeper),
                getPromptsUseCase = getKoin().get(),
                getPromptUseCase = getKoin().get(),
                toggleFavoriteUseCase = getKoin().get(),
                deletePromptUseCase = getKoin().get(),
                deleteAllPromptsUseCase = getKoin().get(),
                createPromptUseCase = getKoin().get(),
                updatePromptUseCase = getKoin().get(),
                getAvailableTagsUseCase = getKoin().get(),
                importJsonUseCase = getKoin().get(),
                parseRawPostsUseCase = getKoin().get(),
                savePromptsAsFilesUseCase = getKoin().get(),
                promptSynchronizer = getKoin().get(),
                promptsRepository = getKoin().get(),
                hybridParser = getKoin().get(),
                httpClient = getKoin().get(),
                systemInteraction = getKoin().get(),
                fileMetadataReader = getKoin().get(),
                llmInteractor = getKoin().get(),
                scrapeUseCase = getKoin().get(),
                webScraper = getKoin().get(),
                processScrapedPostsUseCase = getKoin().get(),
                settingsRepository = getKoin().get(),
                gitHubSyncService = getKoin().get(),
                analyzerPipeline = getKoin().get(),
                importParsedPromptsUseCase = getKoin().get(),
                fileDataSource = getKoin().get(),
                improvePromptUseCase = getKoin().get(),
                providerProbe = getKoin().get(),
            )
        }

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = { sessionStore.save(stateKeeper.save()); exitApplication() },
            state = windowState,
            title = "AI Prompt Master"
        ) {
            com.arny.sharedui.PromptTheme { MainContentDesktopImpl(component = root) }
        }
    }
}
