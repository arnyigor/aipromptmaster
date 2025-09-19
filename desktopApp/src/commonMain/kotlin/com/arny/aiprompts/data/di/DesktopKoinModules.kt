package com.arny.aiprompts.data.di


import com.arny.aiprompts.data.db.AppDatabase
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
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.SeleniumWebScraper
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.presentation.features.llm.DefaultLlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.domain.interfaces.*
import com.arny.aiprompts.di.SettingsFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val desktopDataModule = module {
    single { AppDatabase.getInstance() }
    single { get<AppDatabase>().promptDao() }
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    single { Dispatchers.IO }
}

val desktopFileModule = module {
    singleOf(::FileDataSourceImpl) { bind<FileDataSource>() }
}

val desktopScraperModule = module {
    singleOf(::SeleniumWebScraper) { bind<WebScraper>() }
}

val desktopParserModule = module {
    singleOf(::SimpleParser) { bind<IFileParser>() }
    singleOf(::HybridParserImpl) { bind<IHybridParser>() }
}

val desktopLlmModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    single<LLMService> { NoOpLLMService() }
}

val desktopLlmRepositoriesModule = module {
    singleOf(::OpenRouterRepositoryImpl) { bind<IOpenRouterRepository>() }
    single { SettingsFactory() }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
    singleOf(::ChatHistoryRepositoryImpl) { bind<IChatHistoryRepository>() }
}

val desktopLlmUiModule = module {
    singleOf(::DefaultLlmComponent) { bind<LlmComponent>() }
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
        fileModule
    )