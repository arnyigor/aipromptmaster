package com.arny.aiprompts.data.di


import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.files.FileDataSourceImpl
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.parser.*
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.SeleniumWebScraper
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType
import com.arny.aiprompts.domain.model.ParserConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import java.io.InputStream

// --- МОДУЛИ ДЛЯ DESKTOPMAIN ---

val desktopDataModule = module {
    // База данных (Room - JVM)
    single { AppDatabase.getInstance() }
    single { get<AppDatabase>().promptDao() }
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    single { Dispatchers.IO }

    // Файлы (java.io.File - JVM)
    singleOf(::FileDataSourceImpl) { bind<FileDataSource>() }
}

val desktopScraperModule = module {
    // Скрапер (Selenium - JVM)
    singleOf(::SeleniumWebScraper) { bind<WebScraper>() }
}

val desktopParserModule = module {
    // Парсер (Jsoup - JVM)
    single {
        val inputStream: InputStream = this::class.java.getResourceAsStream("/importer_config.json")!!
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        Json.decodeFromString<Map<String, ParserConfig>>(jsonString)["parserConfig"]!!
    }
    singleOf(::PostClassifier)
    factory { (postType: PostType) ->
        val config: ParserConfig = get()
        when (postType) {
            PostType.STANDARD_PROMPT -> StandardPromptParsingStrategy(config)
            PostType.FILE_ATTACHMENT -> FileAttachmentParsingStrategy(get(), config)
            PostType.EXTERNAL_RESOURCE -> ExternalResourceParsingStrategy(config)
            // ... другие стратегии
            else -> DiscussionParsingStrategy()
        }
    }
    // Связываем интерфейс из commonMain с реализацией из desktopMain
    single<IForumPromptParser> {
        // Убедитесь, что здесь используется ForumPromptParserImpl
        ForumPromptParserImpl(
            classifier = get(),
            strategyFactory = { postType -> get(parameters = { parametersOf(postType) }) }
        )
    }
}

val desktopLlmModule = module {
    // LLM (Ktor CIO - JVM)
    single { HttpClient(CIO) { install(ContentNegotiation) { json() } } }
    single<LLMService> { NoOpLLMService() }
}

// Список всех платформенных модулей
val desktopModules = listOf(desktopDataModule, desktopScraperModule, desktopParserModule, desktopLlmModule)