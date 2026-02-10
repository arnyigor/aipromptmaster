package com.arny.aiprompts.data.di


import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.db.getAppDatabase
import com.arny.aiprompts.data.files.FileDataSourceImpl
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.parser.HybridParserImpl
import com.arny.aiprompts.data.parser.SimpleParser
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.data.repositories.OpenRouterRepositoryImpl
import com.arny.aiprompts.data.repositories.SettingsRepositoryImpl
import com.arny.aiprompts.data.repositories.ChatHistoryRepositoryImpl
import com.arny.aiprompts.presentation.screens.DefaultSettingsComponent
import com.arny.aiprompts.presentation.screens.SettingsComponent
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.DesktopWebScraper
import com.arny.aiprompts.domain.analysis.IAnalyzerPipeline
import com.arny.aiprompts.domain.analysis.PromptAnalyzerPipeline
import com.arny.aiprompts.domain.index.IndexCacheManager
import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.SmartScraper
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.presentation.features.llm.DefaultLlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.domain.interfaces.*
import com.arny.aiprompts.di.SettingsFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val desktopDataModule = module {
    single { getAppDatabase() }
    single { get<AppDatabase>().promptDao() }
    single { get<AppDatabase>().chatSessionDao() }
    single { get<AppDatabase>().chatMessageDao() }
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    single { Dispatchers.IO }
}

val desktopFileModule = module {
    singleOf(::FileDataSourceImpl) { bind<FileDataSource>() }
}

val desktopScraperModule = module {
    singleOf(::DesktopWebScraper) { bind<IWebScraper>() }
}

val desktopParserModule = module {
    singleOf(::SimpleParser) { bind<IFileParser>() }
    singleOf(::HybridParserImpl) { bind<IHybridParser>() }
}

val desktopLlmModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true // может помочь с JSON, содержащим непредсказуемые поля
                })
            }
        }
    }
    single<LLMService> { NoOpLLMService() }

    single<Json> {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    single<IOpenRouterRepository> { OpenRouterRepositoryImpl(get(), get(), get()) } // Передаем зависимости через get()
}

val desktopLlmRepositoriesModule = module {
    single { SettingsFactory() }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
    singleOf(::ChatHistoryRepositoryImpl) { bind<IChatHistoryRepository>() }
    singleOf(::DefaultSettingsComponent) { bind<SettingsComponent>() }
}

val desktopLlmUiModule = module {
    singleOf(::DefaultLlmComponent) { bind<LlmComponent>() }
}

// --- MODULE FOR SCRAPING USE CASES ---
val desktopScrapingUseCasesModule = module {
    single { ExtractPromptDataUseCase() }
    single { AutoCategorizeUseCase() }
    single { ProcessScrapedPostsUseCase(get(), get()) }
}

// --- MODULE FOR INDEX-BASED SCRAPING ---
val desktopIndexScrapingModule = module {
    single { IndexCacheManager() }
    single { IndexParser() }
    single { SmartScraper(get(), get()) }
}

// --- MODULE FOR ANALYZER PIPELINE ---
val desktopAnalyzerModule = module {
    single<IAnalyzerPipeline> { PromptAnalyzerPipeline() }
}

// --- НОВЫЙ МОДУЛЬ ДЛЯ ФАЙЛОВ ---
val fileModule = module {
    singleOf(::FileDataSourceImpl) { bind<FileDataSource>() }
}

val desktopModules =
    listOf(
        desktopDataModule,
        desktopFileModule,
        desktopScraperModule,
        desktopParserModule,
        desktopLlmModule,
        desktopLlmRepositoriesModule,
        desktopLlmUiModule,
        fileModule,
        desktopScrapingUseCasesModule,
        desktopIndexScrapingModule,
        desktopAnalyzerModule
    )