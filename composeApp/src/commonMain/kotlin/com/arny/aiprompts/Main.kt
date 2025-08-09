package com.arny.aiprompts


import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arny.aiprompts.di.dataModule
import com.arny.aiprompts.di.domainModule
import com.arny.aiprompts.presentation.navigation.DefaultRootComponent
import com.arny.aiprompts.presentation.ui.RootContent
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

@OptIn(ExperimentalDecomposeApi::class)
fun main() {
    // 1. Инициализация Koin остается здесь, это можно делать в любом потоке.
    startKoin {
        modules(dataModule, domainModule)
    }

    // 2. Запускаем оконное приложение Compose.
    // Все, что внутри этого блока, будет выполняться в UI-потоке.
    application {
        val windowState = rememberWindowState()

        // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ ---
        // Создаем Lifecycle и RootComponent ВНУТРИ `application` блока

        // 3. Создаем жизненный цикл для Decompose
        val lifecycle = remember { LifecycleRegistry() }

        // 4. Создаем корневой компонент, он теперь будет создан в UI-потоке
        val root = remember {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                // Зависимости по-прежнему получаем из Koin
                getPromptsUseCase = getKoin().get(),
                toggleFavoriteUseCase = getKoin().get()
            )
        }

        // 5. Управляем жизненным циклом Decompose вместе с окном
        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "AI Prompts"
        ) {
            // 6. Вызываем наш корневой Composable
            RootContent(component = root)
        }
    }
}