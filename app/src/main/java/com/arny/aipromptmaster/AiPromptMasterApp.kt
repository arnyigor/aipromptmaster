package com.arny.aipromptmaster

import android.app.Application
import com.arny.aipromptmaster.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class AiPromptMasterApp: Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            // 1️⃣ Android context (необходим для кода, зависящего от контекста)
            androidContext(this@AiPromptMasterApp)

            // 2️⃣ Путь к модулям
            modules(appModule)
        }
    }
}