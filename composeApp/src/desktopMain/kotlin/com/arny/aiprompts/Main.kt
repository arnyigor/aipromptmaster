package com.arny.aiprompts

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arny.aiprompts.data.di.desktopModules
import com.arny.aiprompts.di.commonModules
import com.arny.aiprompts.presentation.navigation.DefaultRootComponent
import com.arny.aiprompts.presentation.ui.RootContent
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

@OptIn(ExperimentalDecomposeApi::class)
fun main() {
    startKoin {
        modules(commonModules + desktopModules)
    }

    application {
        val windowState = rememberWindowState()
        val lifecycle = remember { LifecycleRegistry() }

        val root = remember {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                // Koin предоставляет все эти зависимости
                getPromptsUseCase = getKoin().get(),
                toggleFavoriteUseCase = getKoin().get(),
                scrapeUseCase = getKoin().get(),
                webScraper = getKoin().get(),
                parseRawPostsUseCase = getKoin().get(),
                savePromptsAsFilesUseCase = getKoin().get(),
                hybridParser = getKoin().get(),
                httpClient = getKoin().get()
            )
        }

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "AI Prompt Master"
        ) {
            RootContent(component = root)
        }
    }
}