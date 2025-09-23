package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PlatformFile {
    val name: String

    fun isFile(): Boolean
    suspend fun readText(): String // "Обещаем", что будет suspend-метод для чтения
}

/**
 * Platform detection utility
 */
expect fun getPlatform(): Platform

@Serializable
enum class Platform {
    Android,
    Desktop,
    Web,
    IOS
}
