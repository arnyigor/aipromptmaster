package com.arny.aiprompts.data.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformFile(val file: File) {
    actual val name: String
        get() = file.name

    actual fun isFile(): Boolean = file.isFile()

    // Реализуем suspend-метод для чтения
    actual suspend fun readText(): String = withContext(Dispatchers.IO) {
        file.readText()
    }
}

actual fun getPlatform(): Platform = Platform.Desktop