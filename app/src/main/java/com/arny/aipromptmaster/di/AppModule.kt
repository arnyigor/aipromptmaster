package com.arny.aipromptmaster.di

import org.koin.dsl.module

val appModule = module {
    includes(
        networkModule,
        dataModule,
        domainModule,
        viewModelModule
    )
}
