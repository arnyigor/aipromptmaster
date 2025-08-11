package com.arny.aiprompts.data.di


import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.files.FileDataSourceImpl
import com.arny.aiprompts.data.llm.NoOpLLMService
import com.arny.aiprompts.data.parser.HybridParserImpl
import com.arny.aiprompts.data.parser.SimpleParser
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.data.scraper.SeleniumWebScraper
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.LLMService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
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
    single { HttpClient(CIO) }
    single<LLMService> { NoOpLLMService() }
}

val desktopModules =
    listOf(desktopDataModule, desktopFileModule, desktopScraperModule, desktopParserModule, desktopLlmModule)