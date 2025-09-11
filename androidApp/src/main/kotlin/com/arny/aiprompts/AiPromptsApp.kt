package com.arny.aiprompts

import android.app.Application

class AiPromptsApp: Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidPlatform.init(this)
//        // Инициализируем Koin здесь
//        KoinInitializer.init()
    }
}