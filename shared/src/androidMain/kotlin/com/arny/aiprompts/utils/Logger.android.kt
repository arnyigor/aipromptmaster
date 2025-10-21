package com.arny.aiprompts.utils

import android.util.Log

internal actual fun platformLogDebug(message: String) {
    Log.d("APP_DEBUG", message)
}

internal actual fun platformLogInfo(message: String) {
    Log.i("APP_INFO", message)
}

internal actual fun platformLogWarn(message: String) {
    Log.w("APP_WARN", message)
}

internal actual fun platformLogError(message: String, throwable: Throwable?) {
    Log.e("APP_ERROR", message, throwable)
}