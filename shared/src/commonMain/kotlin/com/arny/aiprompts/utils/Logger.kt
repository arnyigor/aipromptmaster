package com.arny.aiprompts.utils

/**
 * Простая реализация Timber-подобного логирования для Kotlin Multiplatform
 */
object Logger {
    private enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private fun log(level: Level, tag: String?, message: String, throwable: Throwable? = null) {
        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val thread = getCurrentThreadName()
        val tagStr = tag ?: "APP"
        val fullMessage = "[$timestamp] [$thread] $tagStr: $message"

        when (level) {
            Level.DEBUG -> platformLogDebug(fullMessage)
            Level.INFO -> platformLogInfo(fullMessage)
            Level.WARN -> platformLogWarn(fullMessage)
            Level.ERROR -> platformLogError(fullMessage, throwable)
        }
    }

    fun d(tag: String? = null, message: String) {
        log(Level.DEBUG, tag, message)
    }

    fun i(tag: String? = null, message: String) {
        log(Level.INFO, tag, message)
    }

    fun w(tag: String? = null, message: String) {
        log(Level.WARN, tag, message)
    }

    fun e(tag: String? = null, message: String) {
        log(Level.ERROR, tag, message)
    }

    fun e(throwable: Throwable, tag: String? = null, message: String? = null) {
        log(Level.ERROR, tag, message ?: throwable.message ?: "Unknown error", throwable)
    }

    private fun getCurrentThreadName(): String = try {
        Thread.currentThread().name
    } catch (e: Exception) {
        "Thread-${kotlin.random.Random.nextInt()}"
    }
}

// Platform-specific функции (expect/actual)
internal expect fun platformLogDebug(message: String)
internal expect fun platformLogInfo(message: String)
internal expect fun platformLogWarn(message: String)
internal expect fun platformLogError(message: String, throwable: Throwable?)