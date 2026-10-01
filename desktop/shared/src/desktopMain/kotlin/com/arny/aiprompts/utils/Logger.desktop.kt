package com.arny.aiprompts.utils

internal actual fun platformLogDebug(message: String) {
    println("DEBUG: $message")
}

internal actual fun platformLogInfo(message: String) {
    println("INFO: $message")
}

internal actual fun platformLogWarn(message: String) {
    println("WARN: $message")
}

internal actual fun platformLogError(message: String, throwable: Throwable?) {
    println("ERROR: $message")
    throwable?.printStackTrace()
}