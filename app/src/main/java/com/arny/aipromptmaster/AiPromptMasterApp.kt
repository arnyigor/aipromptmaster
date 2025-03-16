package com.arny.aipromptmaster

import com.arny.aipromptmaster.di.DaggerAppComponent
import dagger.android.DaggerApplication
import timber.log.Timber

class AiPromptMasterApp : DaggerApplication() {
    private val applicationInjector = DaggerAppComponent.builder()
        .application(this)
        .aiPromptMasterApp(this)
        .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun applicationInjector() = applicationInjector
}