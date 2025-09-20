package com.arny.aiprompts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import com.arny.aiprompts.presentation.navigation.DefaultRootComponent
import com.arny.aiprompts.presentation.ui.RootContent
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создание root компонента должно происходить вне Compose на главном потоке
        val root = DefaultRootComponent(
            componentContext = defaultComponentContext(),
            getPromptsUseCase = get(),
            getPromptUseCase = get(),
            toggleFavoriteUseCase = get(),
            importJsonUseCase = get(),
            createPromptUseCase = get(),
            updatePromptUseCase = get(),
            deletePromptUseCase = get(),
            getAvailableTagsUseCase = get(),
            scrapeUseCase = get(),
            webScraper = get(),
            parseRawPostsUseCase = get(),
            savePromptsAsFilesUseCase = get(),
            hybridParser = get(),
            httpClient = get(),
            systemInteraction = get(),
            fileMetadataReader = get(),
            llmInteractor = get(),
        )

        setContent {
            RootContent(component = root)
        }
    }
}
