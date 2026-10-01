package com.arny.aiprompts

import com.arny.aiprompts.data.model.Platform
import java.io.File

actual fun getCacheDir(): File {
    return File(System.getProperty("java.io.tmpdir"))
}

actual fun getPlatform(): Platform = Platform.Desktop
