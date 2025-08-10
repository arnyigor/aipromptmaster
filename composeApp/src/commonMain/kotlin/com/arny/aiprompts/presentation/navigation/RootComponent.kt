package com.arny.aiprompts.presentation.navigation


import com.arny.aiprompts.presentation.navigation.RootComponent.Child


import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.domain.interfaces.IHybridParser // <-- Импортируем интерфейс
import com.arny.aiprompts.presentation.ui.importer.DefaultImporterComponent
import com.arny.aiprompts.presentation.screens.DefaultPromptListComponent
import com.arny.aiprompts.presentation.screens.PromptListComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterComponent
import com.arny.aiprompts.presentation.ui.scraper.DefaultScraperComponent
import com.arny.aiprompts.presentation.ui.scraper.ScraperComponent

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data class List(val component: PromptListComponent) : Child
        data class Scraper(val component: ScraperComponent) : Child
        data class Importer(val component: ImporterComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    // Все зависимости, которые понадобятся дочерним компонентам
    private val getPromptsUseCase: GetPromptsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: WebScraper,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val hybridParser: IHybridParser, // Добавляем новую зависимость
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<ScreenConfig>()

    override val stack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = ScreenConfig.serializer(),
            initialConfiguration = ScreenConfig.PromptList, // Стартуем с главного экрана
            handleBackButton = true,
            childFactory = ::createChild
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(config: ScreenConfig, context: ComponentContext): Child {
        return when (config) {
            is ScreenConfig.PromptList -> Child.List(
                DefaultPromptListComponent(
                    componentContext = context,
                    getPromptsUseCase = getPromptsUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    onNavigateToDetails = { /* TODO */ },
                    onNavigateToScraper = { navigation.push(ScreenConfig.Scraper) }
                )
            )

            is ScreenConfig.Scraper -> Child.Scraper(
                DefaultScraperComponent(
                    componentContext = context,
                    scrapeUseCase = scrapeUseCase,
                    webScraper = webScraper,
                    parseRawPostsUseCase = parseRawPostsUseCase,
                    savePromptsAsFilesUseCase = savePromptsAsFilesUseCase,
                    onNavigateToImporter = { files ->
                        if (files.isNotEmpty()) {
                            navigation.push(ScreenConfig.Importer(files))
                        }
                    }
                )
            )

            is ScreenConfig.Importer -> Child.Importer(
                DefaultImporterComponent(
                    componentContext = context,
                    filesToImport = config.files,
                    parseRawPostsUseCase = parseRawPostsUseCase,
                    savePromptsAsFilesUseCase = savePromptsAsFilesUseCase,
                    hybridParser = hybridParser,
                    onBack = { navigation.pop() }
                )
            )
        }
    }
}