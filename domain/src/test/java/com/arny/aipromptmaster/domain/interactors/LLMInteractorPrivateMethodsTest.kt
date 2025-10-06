package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LLMInteractorPrivateMethodsTest {

    @MockK
    private lateinit var modelsRepository: IOpenRouterRepository

    @MockK
    private lateinit var settingsRepository: ISettingsRepository

    @MockK
    private lateinit var historyRepository: IChatHistoryRepository

    @MockK
    private lateinit var fileRepository: IFileRepository

    private lateinit var interactor: LLMInteractor
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        interactor = LLMInteractor(
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
            historyRepository = historyRepository,
            fileRepository = fileRepository
        )
    }

    @Test
    fun `truncateAtWordBoundary should truncate at word boundary when text is long`() {
        // Given
        val longText =
            "This is a very long text that should be truncated at word boundary for better readability"
        val maxLength = 50

        // When
        val result = invokePrivateMethod(
            "truncateAtWordBoundary",
            arrayOf(longText, maxLength)
        ) as String

        // Then
        assertTrue(result.length <= maxLength)
        assertTrue(result.endsWith("readability")) // Should end at word boundary
    }

    @Test
    fun `truncateAtWordBoundary should return original text when shorter than max length`() {
        // Given
        val shortText = "Short text"
        val maxLength = 50

        // When
        val result = invokePrivateMethod(
            "truncateAtWordBoundary",
            arrayOf(shortText, maxLength)
        ) as String

        // Then
        assertEquals(shortText, result)
    }

    @Test
    fun `formatFileSize should format bytes correctly`() {
        // Test bytes
        val bytesResult = invokePrivateMethod("formatFileSize", arrayOf(512L)) as String
        assertEquals("512 B", bytesResult)

        // Test KB
        val kbResult = invokePrivateMethod("formatFileSize", arrayOf(1536L)) as String
        assertEquals("1 KB", kbResult)

        // Test MB
        val mbResult = invokePrivateMethod("formatFileSize", arrayOf(1048576L)) as String
        assertEquals("1.0 MB", mbResult)
    }

    @Test
    fun `getFileExtensionForMarkdown should return correct language for known extensions`() {
        // Test Kotlin
        val kotlinResult =
            invokePrivateMethod("getFileExtensionForMarkdown", arrayOf("kt")) as String
        assertEquals("kotlin", kotlinResult)

        // Test JavaScript
        val jsResult = invokePrivateMethod("getFileExtensionForMarkdown", arrayOf("js")) as String
        assertEquals("javascript", jsResult)

        // Test Python
        val pyResult = invokePrivateMethod("getFileExtensionForMarkdown", arrayOf("py")) as String
        assertEquals("python", pyResult)

        // Test unknown extension
        val unknownResult =
            invokePrivateMethod("getFileExtensionForMarkdown", arrayOf("xyz")) as String
        assertEquals("", unknownResult)
    }

    @Test
    fun `buildMessagesForApi should handle empty history correctly`() = runTest {
        // Given
        val conversationId = "test-id"
        val systemPrompt = "You are a helpful assistant"

        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())

        // When
        val result = invokePrivateSuspendMethod(
            "buildMessagesForApi",
            arrayOf(conversationId, systemPrompt)
        ) as com.arny.aipromptmaster.domain.models.ApiRequestPayload

        // Then
        assertEquals(1, result.messages.size)
        assertEquals("system", result.messages[0].role)
        assertEquals(systemPrompt, result.messages[0].content)
        assertTrue(result.attachedFiles.isEmpty())
    }

    @Test
    fun `buildMessagesForApi should handle messages with file attachments correctly`() = runTest {
        // Given
        val conversationId = "test-id"
        val systemPrompt = "You are a helpful assistant"

        val fileAttachment = FileAttachment(
            id = "file-1",
            fileName = "test.txt",
            fileExtension = "txt",
            fileSize = 100L,
            mimeType = "text/plain",
            originalContent = "File content"
        )

        val messages = listOf(
            ChatMessage(
                role = ChatRole.USER,
                content = "Analyze this file",
                fileAttachment = FileAttachmentMetadata(
                    fileId = "file-1",
                    fileName = "test.txt",
                    fileExtension = "txt",
                    fileSize = 100L,
                    mimeType = "text/plain",
                    preview = "File content"
                )
            )
        )

        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery { fileRepository.getTemporaryFile("file-1") } returns fileAttachment

        // When
        val result = invokePrivateSuspendMethod(
            "buildMessagesForApi",
            arrayOf(conversationId, systemPrompt)
        ) as com.arny.aipromptmaster.domain.models.ApiRequestPayload

        // Then
        assertEquals(3, result.messages.size)

        // First message should be system prompt
        assertEquals("system", result.messages[0].role)
        assertEquals(systemPrompt, result.messages[0].content)

        // Second message should be file instructions
        assertEquals("system", result.messages[1].role)
        assertTrue(result.messages[1].content.contains("📋 **Attached Files Context**"))
        assertTrue(result.messages[1].content.contains("test.txt"))

        // Third message should be user message
        assertEquals("user", result.messages[1].role)
        assertEquals("Analyze this file", result.messages[2].content)

        // File should be in attached files
        assertEquals(1, result.attachedFiles.size)
        assertEquals("file-1", result.attachedFiles[0].id)
    }

    // Helper methods for testing private methods
    private fun invokePrivateMethod(methodName: String, args: Array<Any>): Any? {
        val method = LLMInteractor::class.java.declaredMethods.find { it.name == methodName }
        method?.isAccessible = true
        return method?.invoke(interactor, *args)
    }

    private fun invokePrivateSuspendMethod(methodName: String, args: Array<Any>): Any? {
        val method = LLMInteractor::class.java.declaredMethods.find { it.name == methodName }
        method?.isAccessible = true
        return method?.invoke(interactor, *args)
    }
}