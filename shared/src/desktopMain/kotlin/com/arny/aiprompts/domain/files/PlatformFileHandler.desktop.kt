package com.arny.aiprompts.domain.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Desktop (JVM) реализация PlatformFileHandler.
 */
actual class PlatformFileHandler {

    actual suspend fun readImageToBase64(uriString: String): String = withContext(Dispatchers.IO) {
        val path = uriToPath(uriString)
        val bytes = Files.readAllBytes(path)
        
        // TODO: Добавить ресайз изображения для экономии токенов
        // (макс 2048x2048, сжатие JPG качество 85%)
        
        Base64.getEncoder().encodeToString(bytes)
    }

    actual suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val path = uriToPath(uriString)
        Files.readString(path)
    }

    actual fun getMimeType(uriString: String): String? {
        return try {
            val path = uriToPath(uriString)
            Files.probeContentType(path)
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun copyToInternalStorage(sourceUri: String, targetName: String): String = withContext(Dispatchers.IO) {
        val sourcePath = uriToPath(sourceUri)
        
        // Получаем папку пользователя для приложения
        val userHome = System.getProperty("user.home")
        val attachmentsDir = File(userHome, "aiprompts/attachments").apply { mkdirs() }
        val targetFile = File(attachmentsDir, targetName)
        
        Files.copy(sourcePath, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        
        targetFile.toURI().toString()
    }

    actual fun fileExists(uriString: String): Boolean {
        return try {
            val path = uriToPath(uriString)
            Files.exists(path)
        } catch (e: Exception) {
            false
        }
    }

    actual fun getFileName(uriString: String): String? {
        return try {
            val path = uriToPath(uriString)
            path.fileName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    actual fun getFileSize(uriString: String): Long {
        return try {
            val path = uriToPath(uriString)
            Files.size(path)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Конвертирует URI строку в Path.
     * Поддерживает file:// и обычные пути.
     */
    private fun uriToPath(uriString: String): Path {
        return if (uriString.startsWith("file://")) {
            Paths.get(URI.create(uriString))
        } else {
            Paths.get(uriString)
        }
    }
}

/**
 * Desktop фабрика для PlatformFileHandler.
 */
actual object PlatformFileHandlerFactory {
    actual fun create(): PlatformFileHandler {
        return PlatformFileHandler()
    }
}
