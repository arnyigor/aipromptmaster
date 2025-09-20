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
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.interfaces.IHybridParser
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
    single<FileDataSource> { FileDataSourceImpl(androidContext()) }
    singleOf(::SimpleParser) { bind<IFileParser>() }
    singleOf(::HybridParserImpl) { bind<IHybridParser>() }
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