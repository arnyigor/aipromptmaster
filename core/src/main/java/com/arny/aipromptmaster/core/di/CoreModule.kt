package com.arny.aipromptmaster.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class CoreModule(private val context: Context) {
    @Provides
    @Singleton
    fun provideContext(): Context = context
}