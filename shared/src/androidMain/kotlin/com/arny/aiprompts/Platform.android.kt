package com.arny.aiprompts

import com.arny.aiprompts.data.model.Platform
import java.io.File

actual fun getCacheDir(): File {
    return AndroidPlatform.applicationContext.cacheDir
}

actual fun getPlatform(): Platform = Platform.Android
