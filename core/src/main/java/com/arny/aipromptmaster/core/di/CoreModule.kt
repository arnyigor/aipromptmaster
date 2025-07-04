package com.arny.aipromptmaster.core.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import javax.inject.Singleton

@Module
interface CoreModule {

    companion object {
        @Provides
        @Singleton
        fun provideContext(application: Application): Context = application

        @Provides
        @Singleton
        fun provideMarkwon(context: Context): Markwon {
            return Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .build()
        }
    }
}