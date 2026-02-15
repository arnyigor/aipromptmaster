package com.arny.aiprompts.di

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.api.GitHubServiceImpl
import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.db.getAppDatabase
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.remote.GitHubSyncService
import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.files.FilePromptProcessor
import com.arny.aiprompts.domain.files.PlatformFileHandler
import com.arny.aiprompts.domain.files.PlatformFileHandlerFactory
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.domain.interactors.LLMInteractor
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.system.ActualSystemInteraction
import com.arny.aiprompts.domain.system.SystemInteraction
import com.arny.aiprompts.domain.usecase.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


/**
 * Модуль для слоя данных.
 * Содержит репозитории, DAO, сетевые сервисы и базу данных.
 */
val dataModule = module {
    // Database
    single { getAppDatabase() }
    single { get<AppDatabase>().promptDao() }
    single { get<AppDatabase>().chatSessionDao() }
    single { get<AppDatabase>().chatMessageDao() }

    // Repositories
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
    singleOf(::ChatSessionRepositoryImpl) { bind<IChatSessionRepository>() }
    singleOf(::ChatHistoryRepositoryImpl) { bind<IChatHistoryRepository>() }
    singleOf(::OpenRouterRepositoryImpl) { bind<IOpenRouterRepository>() }

    // NEW: GitHub Sync Service
    singleOf(::GitHubSyncService)

    // JSON Serializer
    single { Json { ignoreUnknownKeys = true; prettyPrint = true } }

    // Dispatchers
    single { Dispatchers.IO }

    // Legacy sync services
    singleOf(::GitHubServiceImpl) { bind<GitHubService>() }
    singleOf(::PromptSynchronizerImpl) { bind<IPromptSynchronizer>() }
}

/**
 * Модуль для скрапера (Desktop-only).
 */
val scraperModule = module {
    singleOf(::ScrapeWebsiteUseCase)
}

/**
 * Модуль для доменного слоя (UseCases).
 */
val commonDomainModule = module {
    // Prompt UseCases
    singleOf(::GetPromptsUseCase)
    singleOf(::GetPromptUseCase)
    singleOf(::ToggleFavoriteUseCase)
    singleOf(::CreatePromptUseCase)
    singleOf(::UpdatePromptUseCase)
    singleOf(::DeletePromptUseCase)
    singleOf(::DeleteAllPromptsUseCase)
    singleOf(::GetAvailableTagsUseCase)

    // Parser UseCases
    singleOf(::ParseHtmlUseCase)
    singleOf(::ParseRawPostsUseCase)
    singleOf(::ProcessScrapedPostsUseCase)
    singleOf(::SavePromptsAsFilesUseCase)

    // Import/Export UseCases
    singleOf(::ImportJsonUseCase)
    singleOf(::ImportFromFileUseCase)
    singleOf(::ExportPromptsUseCase)

    // NEW: Auto-tagging UseCase
    singleOf(::AutoTagPromptUseCase)

    // File utilities
    singleOf(::FileMetadataReader)
    
    // NEW: Platform File Handler (expect/actual)
    single<PlatformFileHandler> { PlatformFileHandlerFactory.create() }
    
    // NEW: File Prompt Processor for multimodal support
    singleOf(::FilePromptProcessor)

    // Interactors
    singleOf(::LLMInteractor) { bind<ILLMInteractor>() }
    singleOf(::PromptSynchronizerImpl) { bind<IPromptSynchronizer>() }

    // System
    single<SystemInteraction> { ActualSystemInteraction() }
}

/**
 * Модуль для LLM и сетевых запросов.
 */
val llmModule = module {
    // HTTP Client
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // Default LLM Service (NoOp - заменить на реальный при необходимости)
    single<LLMService> { NoOpLLMService() }
}

/**
 * Все модули приложения.
 */
val commonModules = listOf(dataModule, commonDomainModule, scraperModule, llmModule)
