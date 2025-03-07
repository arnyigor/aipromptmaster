package com.arny.aipromptmaster.core.di

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun provideContext(application: Application): Context
}