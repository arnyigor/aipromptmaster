package com.arny.aipromptmaster.data.repositories

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.models.FileProcessingResult
import com.arny.aipromptmaster.data.utils.FileUtils
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.services.FileProcessing
import com.arny.aipromptmaster.ui.utils.asString
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FileRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var fileProcessing: FileProcessing
    private lateinit var repository: FileRepositoryImpl

    companion object {
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB
        private const val CHUNK_SIZE = 8192
    }

    @BeforeTest
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        fileProcessing = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver

        // Мок сообщения об ошибке превышения размера файла
        every { context.getString(any(), any()) } returns
                "Размер файла превышает максимально допустимый: 5 MB"

        repository = FileRepositoryImpl(context, fileProcessing)

        // Mock для FileUtils
        mockkObject(FileUtils)
        every { FileUtils.getFileName(any(), any()) } returns "test.txt"
        every { FileUtils.getFileExtension(any()) } returns "txt"
        every { FileUtils.formatFileSize(any()) } returns "5 MB"
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Тесты для saveTemporaryFile ====================

    @Test
    fun `saveTemporaryFile should store file and return its id`() = runTest {
        // Given
        val attachment = createTestFileAttachment()

        // When
        val id = repository.saveTemporaryFile(attachment)

        // Then
        assertEquals(attachment.id, id)
        val retrieved = repository.getTemporaryFile(id)
        assertNotNull(retrieved)
        assertEquals(attachment, retrieved)
    }

    @Test
    fun `saveTemporaryFile should update existing file with same id`() = runTest {
        // Given
        val attachment1 = createTestFileAttachment(id = "test-id", content = "original")
        val attachment2 = createTestFileAttachment(id = "test-id", content = "updated")

        // When
        repository.saveTemporaryFile(attachment1)
        repository.saveTemporaryFile(attachment2)

        // Then
        val retrieved = repository.getTemporaryFile("test-id")
        assertEquals("updated", retrieved?.originalContent)
    }

    // ==================== Тесты для getTemporaryFile ====================

    @Test
    fun `getTemporaryFile should return null for non-existent id`() = runTest {
        // When
        val result = repository.getTemporaryFile("non-existent")

        // Then
        assertNull(result)
    }

    // ==================== Тесты для updateTemporaryFile ====================

    @Test
    fun `updateTemporaryFile should update content and return true`() = runTest {
        // Given
        val attachment = createTestFileAttachment(content = "original")
        repository.saveTemporaryFile(attachment)

        // When
        val result = repository.updateTemporaryFile(attachment.id, "updated")

        // Then
        assertTrue(result)
        val updated = repository.getTemporaryFile(attachment.id)
        assertEquals("updated", updated?.originalContent)
    }

    @Test
    fun `updateTemporaryFile should return false for non-existent file`() = runTest {
        // When
        val result = repository.updateTemporaryFile("non-existent", "content")

        // Then
        assertFalse(result)
    }

    // ==================== Тесты для deleteTemporaryFile ====================

    @Test
    fun `deleteTemporaryFile should remove file from storage`() = runTest {
        // Given
        val attachment = createTestFileAttachment()
        repository.saveTemporaryFile(attachment)

        // When
        repository.deleteTemporaryFile(attachment.id)

        // Then
        val result = repository.getTemporaryFile(attachment.id)
        assertNull(result)
    }

    @Test
    fun `deleteTemporaryFile should not throw on non-existent id`() = runTest {
        // Given
        val nonExistentId = "non-existent-id"

        // When - просто вызываем, не должно быть исключения
        repository.deleteTemporaryFile(nonExistentId)

        // Then - проверяем, что список файлов остался пустым
        val files = repository.getAllTemporaryFiles().first()
        assertTrue(files.isEmpty())
    }

    // ==================== Тесты для getAllTemporaryFiles ====================

    @Test
    fun `getAllTemporaryFiles should emit empty list initially`() = runTest {
        // When
        val files = repository.getAllTemporaryFiles().first()

        // Then
        assertTrue(files.isEmpty())
    }

    @Test
    fun `getAllTemporaryFiles should emit updated list after adding files`() = runTest {
        // Given
        val attachment1 = createTestFileAttachment(id = "1")
        val attachment2 = createTestFileAttachment(id = "2")

        // When
        repository.saveTemporaryFile(attachment1)
        repository.saveTemporaryFile(attachment2)
        val files = repository.getAllTemporaryFiles().first()

        // Then
        assertEquals(2, files.size)
        assertTrue(files.any { it.id == "1" })
        assertTrue(files.any { it.id == "2" })
    }

    @Test
    fun `getAllTemporaryFiles should emit updated list after deletion`() = runTest {
        // Given
        val attachment = createTestFileAttachment()
        repository.saveTemporaryFile(attachment)

        // When
        repository.deleteTemporaryFile(attachment.id)
        val files = repository.getAllTemporaryFiles().first()

        // Then
        assertTrue(files.isEmpty())
    }

    // ==================== Тесты для processFileFromUri - размер файла ====================

    @Test
    fun `processFileFromUri should successfully process file below size limit`() = runTest {
        // Given: файл 3 МБ (ниже лимита 5 МБ)
        val fileSize = 3L * 1024 * 1024
        val uri = mockUriWithContent("a".repeat(fileSize.toInt()), fileSize)
        mockFileProcessingSuccess("Test content", fileSize)

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        assertTrue(results.any { it is FileProcessingResult.Started })
        assertTrue(results.any { it is FileProcessingResult.Complete })

        val startedResult =
            results.first { it is FileProcessingResult.Started } as FileProcessingResult.Started
        assertEquals(fileSize, startedResult.fileSize)
    }


    @Test
    fun `processFileFromUri should throw DomainError when file exceeds size limit`() = runTest {
        // Given: файл 6 МБ (выше лимита 5 МБ)
        val fileSize = 6L * 1024 * 1024
        val uri = mockUriWithContent("a".repeat(fileSize.toInt()), fileSize)

        // When/Then - исключение должно быть выброшено при сборе Flow
        val exception = assertFailsWith<DomainError.Generic> {
            repository.processFileFromUri(uri).collect()
        }

        // ✅ Проверяем stringHolder вместо message
        val errorMessage = exception.stringHolder.asString(context)

        assertTrue(
            errorMessage.contains("превышает максимально допустимый") ||
                    errorMessage.contains("5 MB"),
            "Error message should mention size limit. Actual: $errorMessage"
        )
    }

    @Test
    fun `processFileFromUri should throw when one byte over limit`() = runTest {
        // Given: файл 5 МБ + 1 байт
        val fileSize = MAX_FILE_SIZE_BYTES + 1
        val uri = mockUriWithContent("a".repeat(fileSize.toInt()), fileSize)

        // When/Then
        assertFailsWith<DomainError.Generic> {
            repository.processFileFromUri(uri).collect()
        }
    }

    @Test
    fun `processFileFromUri should not create temporary file when size check fails`() = runTest {
        val uri = mockk<Uri>()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream("a".repeat(6 * 1024 * 1024).toByteArray())

        // When / Then – исключение и отсутствие файла в репозитории
        assertFailsWith<DomainError.Generic> {
            repository.processFileFromUri(uri).collect()
        }

        val files = repository.getAllTemporaryFiles().first()
        assertTrue(files.isEmpty(), "temporary file should not be created")
    }
    @Test
    fun `processFileFromUri should throw when InputStream returns null`() = runTest {
        // Given
        val uri = mockk<Uri>()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
        every { contentResolver.openInputStream(uri) } returns null

        // When / Then – исключение должно быть выброшено при collect()
        assertFailsWith<DomainError.Generic> {
            repository.processFileFromUri(uri).collect()
        }
    }

    @Test
    fun `getSizeForUri should throw when InputStream returns null`() = runTest {
        // Given
        val uri = mockk<Uri>()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
        every { contentResolver.openInputStream(uri) } returns null

        // When/Then - исключение выбрасывается при collect()
        val exception = assertFailsWith<DomainError.Generic> {
            repository.processFileFromUri(uri).collect()
        }

        val errorMessage = exception.stringHolder.asString(context)

        assertTrue(
            errorMessage.contains("Не удалось открыть поток"),
            "Error should mention stream opening failure. Actual: $errorMessage"
        )
    }

    @Test
    fun `processFileFromUri should handle file at exact size limit`() = runTest {
        // Given: файл ровно 5 МБ (на границе лимита)
        val fileSize = MAX_FILE_SIZE_BYTES
        val uri = mockUriWithContent("a".repeat(fileSize.toInt()), fileSize)
        mockFileProcessingSuccess("Content", fileSize)

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        assertTrue(results.any { it is FileProcessingResult.Started })
        val startedResult =
            results.first { it is FileProcessingResult.Started } as FileProcessingResult.Started
        assertEquals(fileSize, startedResult.fileSize)
    }

    // ==================== Тесты для processFileFromUri - обработка файла ====================

    @Test
    fun `processFileFromUri should emit Started event with correct file info`() = runTest {
        // Given
        val fileSize = 1024L
        val fileName = "document.pdf"
        val uri = mockUriWithContent("content", fileSize)
        every { FileUtils.getFileName(context, uri) } returns fileName
        every { FileUtils.getFileExtension(fileName) } returns "pdf"
        mockFileProcessingSuccess("content", fileSize)

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        val started = results.first() as FileProcessingResult.Started
        assertEquals(fileName, started.fileName)
        assertEquals(fileSize, started.fileSize)
    }

    @Test
    fun `processFileFromUri should create FileAttachment with correct metadata`() = runTest {
        // Given
        val fileSize = 2048L
        val fileName = "image.png"
        val mimeType = "image/png"
        val uri = mockUriWithContent("binary content", fileSize)

        every { FileUtils.getFileName(context, uri) } returns fileName
        every { FileUtils.getFileExtension(fileName) } returns "png"
        every { contentResolver.getType(uri) } returns mimeType
        mockFileProcessingSuccess("content", fileSize)

        // When
        repository.processFileFromUri(uri).toList()

        // Then
        val files = repository.getAllTemporaryFiles().first()
        assertEquals(1, files.size)

        val attachment = files.first()
        assertEquals(fileName, attachment.fileName)
        assertEquals("png", attachment.fileExtension)
        assertEquals(fileSize, attachment.fileSize)
        assertEquals(mimeType, attachment.mimeType)
    }

    @Test
    fun `processFileFromUri should emit Progress events`() = runTest {
        // Given
        val fileSize = 10000L
        val uri = mockUriWithContent("content", fileSize)

        every { fileProcessing.processFileFromUriStreaming(uri, CHUNK_SIZE) } returns flowOf(
            FileProcessingResult.Started("test.txt", fileSize),
            FileProcessingResult.Progress(
                progress = 25,
                bytesRead = 2500,
                totalBytes = fileSize,
                previewText = "Preview 1"
            ),
            FileProcessingResult.Progress(
                progress = 50,
                bytesRead = 5000,
                totalBytes = fileSize,
                previewText = "Preview 2"
            ),
            FileProcessingResult.Progress(
                progress = 75,
                bytesRead = 7500,
                totalBytes = fileSize,
                previewText = "Preview 3"
            ),
            FileProcessingResult.Complete(
                text = "Final content",
                fileAttachment = createTestFileAttachment()
            )
        )

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        val progressEvents = results.filterIsInstance<FileProcessingResult.Progress>()
        assertEquals(3, progressEvents.size)

        // Проверяем progress (проценты)
        assertEquals(25, progressEvents[0].progress)
        assertEquals(50, progressEvents[1].progress)
        assertEquals(75, progressEvents[2].progress)

        // Проверяем bytesRead
        assertEquals(2500L, progressEvents[0].bytesRead)
        assertEquals(5000L, progressEvents[1].bytesRead)
        assertEquals(7500L, progressEvents[2].bytesRead)
    }

    @Test
    fun `processFileFromUri should update file content on Complete event`() = runTest {
        // Given
        val fileSize = 1024L
        val finalContent = "Processed file content"
        val uri = mockUriWithContent("raw", fileSize)
        mockFileProcessingSuccess(finalContent, fileSize)

        // When
        repository.processFileFromUri(uri).toList()

        // Then
        val files = repository.getAllTemporaryFiles().first()
        val attachment = files.first()
        assertEquals(finalContent, attachment.originalContent)
    }

    @Test
    fun `processFileFromUri should propagate Error events`() = runTest {
        // Given
        val fileSize = 1024L
        val errorMessage = "Failed to process file"
        val uri = mockUriWithContent("content", fileSize)

        every { fileProcessing.processFileFromUriStreaming(uri, CHUNK_SIZE) } returns flowOf(
            FileProcessingResult.Started("test.txt", fileSize),
            FileProcessingResult.Error(errorMessage)
        )

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        assertTrue(results.any { it is FileProcessingResult.Error })
        val error = results.first { it is FileProcessingResult.Error } as FileProcessingResult.Error
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun `processFileFromUri should handle unknown mime type gracefully`() = runTest {
        // Given
        val fileSize = 1024L
        val uri = mockUriWithContent("content", fileSize)
        every { contentResolver.getType(uri) } returns null
        mockFileProcessingSuccess("content", fileSize)

        // When
        repository.processFileFromUri(uri).toList()

        // Then
        val files = repository.getAllTemporaryFiles().first()
        val attachment = files.first()
        assertEquals("", attachment.mimeType) // Должен быть пустой строкой, не null
    }

    // ==================== Тесты для getSizeForUri (косвенно через processFileFromUri) ====================

    @Test
    fun `getSizeForUri should use AssetFileDescriptor when available`() = runTest {
        // Given
        val expectedSize = 4096L
        val uri = mockk<Uri>()
        val afd = mockk<AssetFileDescriptor>()

        every { afd.length } returns expectedSize
        every { afd.close() } just Runs
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns afd
        every { contentResolver.getType(uri) } returns "text/plain"

        mockFileProcessingSuccess("content", expectedSize)

        // When
        val results = repository.processFileFromUri(uri).toList()

        // Then
        val started =
            results.first { it is FileProcessingResult.Started } as FileProcessingResult.Started
        assertEquals(expectedSize, started.fileSize)
        verify { afd.close() }
    }

    @Test
    fun `getSizeForUri should fallback to InputStream when AssetFileDescriptor unavailable`() =
        runTest {
            // Given
            val content = "a".repeat(2048)
            val uri = mockk<Uri>()

            every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
            every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(content.toByteArray())
            every { contentResolver.getType(uri) } returns "text/plain"

            mockFileProcessingSuccess("content", content.length.toLong())

            // When
            val results = repository.processFileFromUri(uri).toList()

            // Then
            val started =
                results.first { it is FileProcessingResult.Started } as FileProcessingResult.Started
            assertEquals(content.length.toLong(), started.fileSize)
        }

    @Test
    fun `getSizeForUri should stop reading immediately when size exceeds limit`() = runTest {
        // Given: создаём очень большой поток, но ожидаем раннего выхода
        val uri = mockk<Uri>()
        var totalBytesRead = 0
        val largeInputStream = object : InputStream() {
            override fun read(b: ByteArray): Int {
                totalBytesRead += b.size
                return b.size // Всегда возвращаем полный буфер
            }

            override fun read(): Int = 'a'.code
        }

        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
        every { contentResolver.openInputStream(uri) } returns largeInputStream

        // When
        try {
            repository.processFileFromUri(uri).first()
            fail("Should throw DomainError.Generic")
        } catch (e: DomainError.Generic) {
            // Expected
        }

        // Then: проверяем, что не прочитали больше, чем нужно для проверки лимита
        // Максимум должно быть прочитано MAX_FILE_SIZE_BYTES + 1 буфер (8192)
        assertTrue(
            totalBytesRead <= MAX_FILE_SIZE_BYTES + CHUNK_SIZE,
            "Should stop reading early, but read $totalBytesRead bytes"
        )
    }

    // ==================== Helper методы ====================

    private fun createTestFileAttachment(
        id: String = "test-id-${System.currentTimeMillis()}",
        fileName: String = "test.txt",
        content: String = "test content"
    ): FileAttachment {
        return FileAttachment(
            id = id,
            fileName = fileName,
            fileExtension = "txt",
            fileSize = content.length.toLong(),
            mimeType = "text/plain",
            originalContent = content
        )
    }

    private fun mockUriWithContent(
        content: String,
        reportedSize: Long? = null
    ): Uri {
        val uri = mockk<Uri>()
        val inputStream = ByteArrayInputStream(content.toByteArray())

        if (reportedSize != null && reportedSize > 0) {
            val afd = mockk<AssetFileDescriptor>()
            every { afd.length } returns reportedSize
            every { afd.close() } just Runs
            every { contentResolver.openAssetFileDescriptor(uri, "r") } returns afd
        } else {
            every { contentResolver.openAssetFileDescriptor(uri, "r") } returns null
        }

        every { contentResolver.openInputStream(uri) } returns inputStream
        every { contentResolver.getType(uri) } returns "text/plain"

        return uri
    }

    private fun mockFileProcessingSuccess(
        finalContent: String,
        fileSize: Long,
        fileName: String = "test.txt"
    ) {
        every {
            fileProcessing.processFileFromUriStreaming(any(), CHUNK_SIZE)
        } returns flowOf(
            FileProcessingResult.Started(fileName, fileSize),
            FileProcessingResult.Complete(
                text = finalContent,
                fileAttachment = FileAttachment(
                    fileName = fileName,
                    fileExtension = FileUtils.getFileExtension(fileName),
                    fileSize = fileSize,
                    mimeType = "text/plain",
                    originalContent = finalContent
                )
            )
        )
    }
}
