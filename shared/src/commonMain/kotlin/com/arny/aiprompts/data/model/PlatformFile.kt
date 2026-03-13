package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

/**
 * Platform-agnostic file representation.
 * Use [createPlatformFile] function to instantiate.
 */
interface PlatformFile {
    val name: String
    val path: String
    
    fun exists(): Boolean
    suspend fun readText(): String
    suspend fun readBytes(): ByteArray
    suspend fun getSize(): Long
    fun getMimeType(): String
}

@Serializable
enum class Platform {
    Android,
    Desktop,
    Web,
    IOS
}

/**
 * Creates a PlatformFile from a path string.
 * For Desktop, uses JVM File directly.
 * For Android, wraps in appropriate handler.
 */
fun createPlatformFile(pathOrUri: String): PlatformFile {
    return PlatformFileImpl(pathOrUri)
}

/**
 * Internal implementation using JVM File.
 * Works for both Desktop and Android file paths.
 */
internal class PlatformFileImpl(private val pathOrUri: String) : PlatformFile {
    private val file: java.io.File = java.io.File(pathOrUri)
    
    override val name: String get() = file.name
    override val path: String get() = file.absolutePath
    
    override fun exists(): Boolean = file.exists()
    
    override suspend fun readText(): String = file.readText()
    
    override suspend fun readBytes(): ByteArray = file.readBytes()
    
    override suspend fun getSize(): Long = file.length()
    
    override fun getMimeType(): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
