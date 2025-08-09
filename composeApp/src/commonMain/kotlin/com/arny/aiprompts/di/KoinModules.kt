package com.arny.aiprompts.di

import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.files.FileDataSourceImpl
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.parser.DiscussionParsingStrategy
import com.arny.aiprompts.data.parser.ForumPromptParser
import com.arny.aiprompts.data.parser.IForumPromptParser
import com.arny.aiprompts.data.parser.PostClassifier
import com.arny.aiprompts.data.parser.StandardPromptParsingStrategy
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.SeleniumWebScraper
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ParseHtmlUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
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
val domainModule = module {
    singleOf(::GetPromptsUseCase)
    singleOf(::ToggleFavoriteUseCase)
    singleOf(::ParseHtmlUseCase)
    singleOf(::SavePromptsAsFilesUseCase)
}

val parserModule = module {
    // PostClassifier - все в порядке
    singleOf(::PostClassifier)
    single { PostClassifier(get()) }
    // Фабрика стратегий - все в порядке
    factory { (postType: PostType) ->
        when (postType) {
            PostType.STANDARD_PROMPT -> StandardPromptParsingStrategy()
            else -> DiscussionParsingStrategy()
        }
    }

    // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ ---
    // Заменяем singleOf(::ForumPromptParser) на ручное определение
    single<IForumPromptParser> {
        // Мы вручную создаем экземпляр ForumPromptParser
        ForumPromptParser(
            // 1. Koin сам найдет PostClassifier, т.к. он определен выше
            classifier = get(),
            // 2. А вот фабрику стратегий мы создаем здесь же, вручную
            strategyFactory = { postType ->
                // get() с параметрами - это способ получить factory-зависимость
                get(parameters = { parametersOf(postType) })
            }
        )
    }
}

val llmModule = module {
    // Создаем HTTP клиент для Ktor
    single {
        HttpClient(CIO) {
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

// --- НОВЫЙ МОДУЛЬ ДЛЯ ФАЙЛОВ ---
val fileModule = module {
    singleOf(::FileDataSourceImpl) { bind<FileDataSource>() }
}

val appModules = listOf(dataModule, domainModule, scraperModule, parserModule, llmModule, fileModule)