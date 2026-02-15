package com.arny.aiprompts.domain.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Android реализация PlatformFileHandler.
 */
actual class PlatformFileHandler(private val context: Context) {

    actual suspend fun readImageToBase64(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: throw IOException("Cannot read image file: $uriString")
        
        // TODO: Добавить ресайз изображения для экономии токенов
        // (макс 2048x2048, сжатие JPG качество 85%)
        
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    actual suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { it.readText() }
        } ?: throw IOException("Cannot read text file: $uriString")
    }

    actual fun getMimeType(uriString: String): String? {
        val uri = Uri.parse(uriString)
        return context.contentResolver.getType(uri)
    }

    actual suspend fun copyToInternalStorage(sourceUri: String, targetName: String): String = withContext(Dispatchers.IO) {
        val source = Uri.parse(sourceUri)
        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val targetFile = File(attachmentsDir, targetName)
        
        context.contentResolver.openInputStream(source)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot copy file from $sourceUri")
        
        targetFile.toURI().toString()
    }

    actual fun fileExists(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    actual fun getFileName(uriString: String): String? {
        val uri = Uri.parse(uriString)
        var fileName: String? = null
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        return fileName ?: uri.lastPathSegment
    }

    actual fun getFileSize(uriString: String): Long {
        val uri = Uri.parse(uriString)
        var size: Long = 0
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        
        return size
    }
}

/**
 * Android фабрика для PlatformFileHandler.
 */
actual object PlatformFileHandlerFactory {
    private var context: Context? = null
    
    fun initialize(ctx: Context) {
        context = ctx.applicationContext
    }
    
    actual fun create(): PlatformFileHandler {
        return PlatformFileHandler(
            context ?: throw IllegalStateException("PlatformFileHandlerFactory not initialized")
        )
    }
}
