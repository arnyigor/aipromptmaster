package com.arny.aiprompts

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
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
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    startKoin {
        modules(commonModules + desktopModules)
    }

    application {
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(1000.dp, 800.dp)
        )
        val lifecycle = remember { LifecycleRegistry() }

        val root = remember {
            DefaultMainComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                getPromptsUseCase = getKoin().get(),
                getPromptUseCase = getKoin().get(),
                toggleFavoriteUseCase = getKoin().get(),
                deletePromptUseCase = getKoin().get(),
                createPromptUseCase = getKoin().get(),
                updatePromptUseCase = getKoin().get(),
                getAvailableTagsUseCase = getKoin().get(),
                importJsonUseCase = getKoin().get(),
                parseRawPostsUseCase = getKoin().get(),
                savePromptsAsFilesUseCase = getKoin().get(),
                hybridParser = getKoin().get(),
                httpClient = getKoin().get(),
                systemInteraction = getKoin().get(),
                fileMetadataReader = getKoin().get(),
                llmInteractor = getKoin().get()
            )
        }

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "AI Prompt Master"
        ) {
            MainContentDesktopImpl(component = root)
        }
    }
}