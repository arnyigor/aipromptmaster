package com.arny.aiprompts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import com.arny.aiprompts.domain.files.PlatformFileHandlerFactory
import com.arny.aiprompts.presentation.navigation.DefaultMainComponent
import com.arny.aiprompts.presentation.ui.MainContentAndroid
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PlatformFileHandlerFactory for file operations
        PlatformFileHandlerFactory.initialize(this)

        val root = DefaultMainComponent(
            componentContext = defaultComponentContext(),
            getPromptsUseCase = get(),
            getPromptUseCase = get(),
            toggleFavoriteUseCase = get(),
            deletePromptUseCase = get(),
            deleteAllPromptsUseCase = get(),
            createPromptUseCase = get(),
            updatePromptUseCase = get(),
            getAvailableTagsUseCase = get(),
            importJsonUseCase = get(),
            parseRawPostsUseCase = get(),
            savePromptsAsFilesUseCase = get(),
            promptSynchronizer = get(),
            promptsRepository = get(),
            hybridParser = get(),
            httpClient = get(),
            systemInteraction = get(),
            fileMetadataReader = get(),
            llmInteractor = get(),
            scrapeUseCase = get(),
            webScraper = get(),
            settingsRepository = get(),
            gitHubSyncService = get(),
            processScrapedPostsUseCase = get(),
            analyzerPipeline = get(),
            importParsedPromptsUseCase = get()
        )

        setContent {
            MainContentAndroid(component = root)
        }
    }
}
