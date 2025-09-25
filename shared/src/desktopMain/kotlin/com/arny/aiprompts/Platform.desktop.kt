package com.arny.aiprompts

import java.io.File

actual fun getCacheDir(): File {
    return File(System.getProperty("java.io.tmpdir"))
}