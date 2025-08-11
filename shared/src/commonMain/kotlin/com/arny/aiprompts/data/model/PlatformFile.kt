package com.arny.aiprompts.data.model

expect class PlatformFile {
    val name: String

    fun isFile(): Boolean
    suspend fun readText(): String // "Обещаем", что будет suspend-метод для чтения
}