package com.arny.aiprompts

import java.io.File

actual fun getCacheDir(): File {
    return AndroidPlatform.applicationContext.cacheDir
}