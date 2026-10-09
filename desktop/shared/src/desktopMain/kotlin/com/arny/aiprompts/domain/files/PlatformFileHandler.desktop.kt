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
        require(Files.size(path) <= 10L * 1024 * 1024) { "Изображение больше 10 МБ" }
        javax.imageio.ImageIO.createImageInputStream(path.toFile()).use { input ->
            require(input != null) { "Не удалось прочитать изображение" }
            val readers = javax.imageio.ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Формат изображения не поддерживается" }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                require(width.toLong() * height <= 40_000_000) { "Изображение имеет слишком большое разрешение" }
                val format = reader.formatName.lowercase()
                val parameters = reader.defaultReadParam
                val sample = (maxOf(width, height) / 2048).coerceAtLeast(1)
                parameters.setSourceSubsampling(sample, sample, 0, 0)
                val source = reader.read(0, parameters)
                val scale = minOf(1.0, 2048.0 / maxOf(source.width, source.height))
                val image = if (scale == 1.0) source else java.awt.image.BufferedImage(
                    (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1),
                    if (format in setOf("jpeg", "jpg")) java.awt.image.BufferedImage.TYPE_INT_RGB else java.awt.image.BufferedImage.TYPE_INT_ARGB).also { target ->
                        val graphics = target.createGraphics()
                        try { graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            graphics.drawImage(source, 0, 0, target.width, target.height, null)
                        } finally { graphics.dispose() }
                    }
                val output = java.io.ByteArrayOutputStream()
                require(javax.imageio.ImageIO.write(image, format, output)) { "Формат изображения не поддерживается" }
                Base64.getEncoder().encodeToString(output.toByteArray())
            } finally { reader.dispose() }
        }
    }

    actual suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val path = uriToPath(uriString)
        require(Files.size(path) <= 2L * 1024 * 1024) { "Текстовый файл больше 2 МБ" }
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
        require(Files.size(sourcePath) <= 10L * 1024 * 1024) { "Вложение больше 10 МБ" }
        val safeName = targetName.substringAfterLast('/').substringAfterLast('\\').take(200)
        require(safeName.isNotBlank() && safeName !in setOf(".", "..")) { "Некорректное имя вложения" }
        val targetFile = File(attachmentsDir, safeName)
        require(targetFile.toPath().normalize().startsWith(attachmentsDir.toPath().normalize()))
        
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
