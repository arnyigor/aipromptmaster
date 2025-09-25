package com.arny.aiprompts.di

import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.SeleniumWebScraper
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.domain.interactors.LLMInteractor
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.system.ActualSystemInteraction
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


// Модуль для слоя данных
val dataModule = module {
    // Создаем синглтон AppDatabase
    single { AppDatabase.getInstance() }
    // Создаем синглтон PromptDao
    single { get<AppDatabase>().promptDao() }
    // Создаем синглтон PromptsRepositoryImpl и связываем его с интерфейсом IPromptsRepository
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    // Предоставляем диспатчер для фоновых задач
    single { Dispatchers.IO }
}

// Добавляем новый модуль для скрапера
val scraperModule = module {
    // Используем singleOf, так как SeleniumWebScraper не имеет зависимостей
    singleOf(::SeleniumWebScraper) { bind<WebScraper>() }
    singleOf(::ScrapeWebsiteUseCase)
}

// Модуль для доменного слоя (UseCases)
val commonDomainModule = module {
    singleOf(::GetPromptsUseCase)
    singleOf(::GetPromptUseCase)
    singleOf(::ToggleFavoriteUseCase)
    singleOf(::CreatePromptUseCase)
    singleOf(::UpdatePromptUseCase)
    singleOf(::DeletePromptUseCase)
    singleOf(::DeleteAllPromptsUseCase)
    singleOf(::GetAvailableTagsUseCase)
    singleOf(::ParseHtmlUseCase)
    singleOf(::ParseRawPostsUseCase)
    singleOf(::SavePromptsAsFilesUseCase)
    singleOf(::ImportJsonUseCase)
    singleOf(::ImportFromFileUseCase)
    singleOf(::ExportPromptsUseCase)
    singleOf(::FileMetadataReader)
    singleOf(::LLMInteractor) { bind<ILLMInteractor>() }
    single<SystemInteraction> { ActualSystemInteraction() }
}

val llmModule = module {
    // Создаем HTTP клиент для Ktor
    single {
        HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }
    // По умолчанию используем "пустышку"
    single<LLMService> { NoOpLLMService() }
    // Если понадобится реальный сервис:
    // single<LLMService> { OllamaLLMService(get()) }
}


val commonModules = listOf(dataModule, commonDomainModule, scraperModule, llmModule)