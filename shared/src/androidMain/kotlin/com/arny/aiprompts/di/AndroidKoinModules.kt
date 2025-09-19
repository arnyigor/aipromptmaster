package com.arny.aiprompts.di

import android.content.Context
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.SettingsRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidModules = module {
    single { SettingsFactory(androidContext()) }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
}