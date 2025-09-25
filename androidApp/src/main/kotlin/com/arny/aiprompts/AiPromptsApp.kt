package com.arny.aiprompts

import android.app.Application
import com.arny.aiprompts.di.androidModules
import com.arny.aiprompts.di.commonModules
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

class AiPromptsApp: Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)

        startKoin {
            androidContext(this@AiPromptsApp)
            modules(commonModules + androidModules)
        }

        // Запуск синхронизации промптов в фоне
        CoroutineScope(Dispatchers.IO).launch {
            val synchronizer = getKoin().get<IPromptSynchronizer>()
            synchronizer.synchronize()
        }
    }
}
