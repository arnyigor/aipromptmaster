package com.arny.aiprompts.di

import android.content.Context
import com.arny.aiprompts.data.files.FileDataSourceImpl
import com.arny.aiprompts.data.parser.SimpleParser
import com.arny.aiprompts.data.parser.HybridParserImpl
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.data.repositories.SettingsRepositoryImpl
import com.arny.aiprompts.data.repositories.ChatHistoryRepositoryImpl
import com.arny.aiprompts.data.repositories.OpenRouterRepositoryImpl
import com.arny.aiprompts.data.scraper.NoOpWebScraper
import com.arny.aiprompts.domain.analysis.IAnalyzerPipeline
import com.arny.aiprompts.domain.analysis.NoOpAnalyzerPipeline
import com.arny.aiprompts.presentation.screens.DefaultSettingsComponent
import com.arny.aiprompts.presentation.screens.SettingsComponent
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.interfaces.IWebScraper
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidModules = module {
    single { SettingsFactory(androidContext()) }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
    singleOf(::ChatHistoryRepositoryImpl) { bind<IChatHistoryRepository>() }
    singleOf(::OpenRouterRepositoryImpl) { bind<IOpenRouterRepository>() }
    singleOf(::DefaultSettingsComponent) { bind<SettingsComponent>() }
    single<FileDataSource> { FileDataSourceImpl(androidContext()) }
    singleOf(::SimpleParser) { bind<IFileParser>() }
    singleOf(::HybridParserImpl) { bind<IHybridParser>() }
    // Stub for Android - scraping not supported
    singleOf(::NoOpWebScraper) { bind<IWebScraper>() }
    // Stub for Android - analyzer pipeline not supported
    singleOf(::NoOpAnalyzerPipeline) { bind<IAnalyzerPipeline>() }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
}