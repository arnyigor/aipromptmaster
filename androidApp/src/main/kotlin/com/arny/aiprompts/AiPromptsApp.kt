package com.arny.aiprompts

import android.app.Application
import com.arny.aiprompts.di.androidModules
import com.arny.aiprompts.di.commonModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AiPromptsApp: Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)

        startKoin {
            androidContext(this@AiPromptsApp)
            modules(commonModules + androidModules)
        }
    }
}
