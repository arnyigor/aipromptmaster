package com.arny.aiprompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.arny.aiprompts.presentation.navigation.DefaultMainComponent
import com.arny.aiprompts.presentation.ui.MainContent
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import io.ktor.client.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Composable
fun App() {
    // Get dependencies from Koin
    val appComponent: AppComponent by remember { inject() }

    // Create MainComponent with all required dependencies
    val mainComponent = remember {
        DefaultMainComponent(
            componentContext = appComponent.componentContext,
            getPromptsUseCase = appComponent.getPromptsUseCase,
            getPromptUseCase = appComponent.getPromptUseCase,
            toggleFavoriteUseCase = appComponent.toggleFavoriteUseCase,
            importJsonUseCase = appComponent.importJsonUseCase,
            scrapeUseCase = appComponent.scrapeUseCase,
            webScraper = appComponent.webScraper,
            parseRawPostsUseCase = appComponent.parseRawPostsUseCase,
            savePromptsAsFilesUseCase = appComponent.savePromptsAsFilesUseCase,
            hybridParser = appComponent.hybridParser,
            httpClient = appComponent.httpClient,
            systemInteraction = appComponent.systemInteraction,
            fileMetadataReader = appComponent.fileMetadataReader,
            llmInteractor = appComponent.llmInteractor,
        )
    }

    // Use the common MainContent that routes to platform-specific implementations
    MainContent(
        component = mainComponent,
        modifier = androidx.compose.ui.Modifier
    )
}

// AppComponent interface for dependency injection
interface AppComponent : KoinComponent {
    val componentContext: com.arkivanov.decompose.ComponentContext
    val getPromptsUseCase: GetPromptsUseCase
    val getPromptUseCase: GetPromptUseCase
    val toggleFavoriteUseCase: ToggleFavoriteUseCase
    val importJsonUseCase: ImportJsonUseCase
    val scrapeUseCase: ScrapeWebsiteUseCase
    val webScraper: WebScraper
    val parseRawPostsUseCase: ParseRawPostsUseCase
    val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase
    val hybridParser: IHybridParser
    val httpClient: HttpClient
    val systemInteraction: SystemInteraction
    val fileMetadataReader: FileMetadataReader
    val llmInteractor: ILLMInteractor
}