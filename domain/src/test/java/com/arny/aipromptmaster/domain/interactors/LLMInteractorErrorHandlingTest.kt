package com.arny.aipromptmaster.domain.interactors

import app.cash.turbine.test
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class LLMInteractorErrorHandlingTest {

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
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        interactor = LLMInteractor(
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
            historyRepository = historyRepository,
            fileRepository = fileRepository
        )
    }

    @Test
    fun `should handle API key not found error correctly`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"

        coEvery { settingsRepository.getApiKey() } returns null
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())

        // When & Then
        interactor.sendMessage(model, conversationId).test {
            assertEquals(DataResult.Loading, awaitItem())

            val error = awaitItem() as DataResult.Error
            assertTrue(error.exception is DomainError.Local)

            awaitComplete()
        }
    }

    @Test
    fun `should handle empty API key error correctly`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"

        coEvery { settingsRepository.getApiKey() } returns "   " // Only whitespace
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())

        // When & Then
        interactor.sendMessage(model, conversationId).test {
            assertEquals(DataResult.Loading, awaitItem())

            val error = awaitItem() as DataResult.Error
            assertTrue(error.exception is DomainError.Local)

            awaitComplete()
        }
    }

    @Test
    fun `should handle API errors correctly`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val apiErrors = listOf(
            DomainError.Api(400, "Bad Request", "Invalid model"),
            DomainError.Api(401, "Unauthorized", "Invalid API key"),
            DomainError.Api(429, "Rate Limited", "Too many requests"),
            DomainError.Api(500, "Internal Server Error", "Server error")
        )

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)

        apiErrors.forEach { error ->
            coEvery { modelsRepository.getChatCompletion(model, messages, apiKey) } returns Result.failure(error)

            // When & Then
            interactor.sendMessage(model, conversationId).test {
                assertEquals(DataResult.Loading, awaitItem())

                val errorResult = awaitItem() as DataResult.Error
                assertEquals(error, errorResult.exception)

                awaitComplete()
            }
        }
    }

    @Test
    fun `should handle network errors correctly`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val networkErrors = listOf(
            DomainError.network(R.string.error_timeout),
            DomainError.network(R.string.error_no_internet),
            DomainError.network(R.string.error_connection),
            DomainError.Generic("Unknown network error")
        )

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)

        networkErrors.forEach { error ->
            coEvery { modelsRepository.getChatCompletion(model, messages, apiKey) } returns Result.failure(error)

            // When & Then
            interactor.sendMessage(model, conversationId).test {
                assertEquals(DataResult.Loading, awaitItem())

                val errorResult = awaitItem() as DataResult.Error
                assertEquals(error, errorResult.exception)

                awaitComplete()
            }
        }
    }

    @Test
    fun `should handle conversation not found error in export`() = runTest {
        // Given
        val conversationId = "non-existent-conversationId"
        coEvery { historyRepository.getConversation(conversationId) } returns null

        // When & Then
        try {
            interactor.getFullChatForExport(conversationId)
            assert(false) { "Should have thrown exception" }
        } catch (e: Exception) {
            assertTrue(e is DomainError.Local)
        }
    }

    @Test
    fun `should handle file not found error in export gracefully`() = runTest {
        // Given
        val conversationId = "test-conversationId"
        val conversation = Conversation(
            id = conversationId,
            title = "Test Chat",
            systemPrompt = "System prompt"
        )

        val messages = listOf(
            ChatMessage(
                role = ChatRole.USER,
                content = "Check this file",
                fileAttachment = FileAttachmentMetadata(
                    fileId = "missing-file",
                    fileName = "missing.txt",
                    fileExtension = "txt",
                    fileSize = 100L,
                    mimeType = "text/plain",
                    preview = "Preview"
                )
            )
        )

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns messages
        coEvery { fileRepository.getTemporaryFile("missing-file") } returns null

        // When
        val result = interactor.getFullChatForExport(conversationId)

        // Then
        assertTrue("Should contain chat title", result.contains("Test Chat"))
        assertTrue("Should contain system prompt", result.contains("System prompt"))
        // File should not be included since it's missing
        assertTrue("File should not be included when missing", !result.contains("missing.txt"))
        // But the message about the file should still be there
        assertTrue("Message about file should be present", result.contains("Check this file"))
        // Проверяем, что экспорт не пустой
        assertTrue("Export should not be empty", result.isNotBlank())
    }

    @Test
    fun `should handle streaming errors correctly in sendMessageWithFallback`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"

        val conversation = Conversation(conversationId, "Test Chat", "System prompt")
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns "System prompt"
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-conversationId"
        coEvery { historyRepository.deleteMessage("message-conversationId") } returns Unit
        coEvery { modelsRepository.getChatCompletionStream(any(), any(), apiKey) } returns flowOf(
            DataResult.Error(DomainError.network(R.string.error_connection))
        )

        // When & Then
        try {
            interactor.sendMessageWithFallback(model, conversationId)
            assert(false) { "Should have thrown exception" }
        } catch (e: Exception) {
            assertTrue(e is DomainError)
        }

        // Verify message was deleted on error
        coVerify { historyRepository.deleteMessage("message-conversationId") }
    }

    @Test
    fun `should handle cancellation correctly during streaming`() {
        // Given
        coEvery { modelsRepository.cancelCurrentRequest() } returns Unit

        // When
        interactor.cancelCurrentRequest()

        // Then
        coVerify { modelsRepository.cancelCurrentRequest() }
    }

    // ✅ УДАЛЕНЫ ТЕСТЫ ДЛЯ ПРИВАТНОГО МЕТОДА buildMessagesForApi
    // Вместо этого тестируем публичный API через sendMessageWithFallback

    @Test
    fun `should handle empty messages list correctly through sendMessageWithFallback`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"
        val systemPrompt = "You are a helpful assistant"

        val conversation = Conversation(conversationId, "Test Chat", systemPrompt)

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns systemPrompt
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-conversationId"
        coEvery { historyRepository.appendContentToMessage(any(), any()) } returns Unit
        coEvery { modelsRepository.getChatCompletionStream(any(), any(), apiKey) } returns flowOf(
            DataResult.Success("Test response")
        )

        // When
        interactor.sendMessageWithFallback(model, conversationId)

        // Then - Should not throw exception even with empty history
        coVerify { historyRepository.appendContentToMessage("message-conversationId", "Test response") }
    }

    @Test
    fun `should handle null system prompt correctly through sendMessageWithFallback`() = runTest {
        // Given
        val model = "gpt-3.5-turbo"
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val conversation = Conversation(conversationId, "Test Chat", null)

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns null
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-conversationId"
        coEvery { historyRepository.appendContentToMessage(any(), any()) } returns Unit
        coEvery { modelsRepository.getChatCompletionStream(any(), any(), apiKey) } returns flowOf(
            DataResult.Success("Test response")
        )

        // When
        interactor.sendMessageWithFallback(model, conversationId)

        // Then - Should work without system prompt
        coVerify { historyRepository.appendContentToMessage("message-conversationId", "Test response") }
    }

    @Test
    fun `should handle repository exceptions correctly in all operations`() = runTest {
        // Given
        val conversationId = "test-conversationId"

        // Test createNewConversation with repository error
        coEvery { historyRepository.createNewConversation("Test") } throws RuntimeException("Database error")

        try {
            interactor.createNewConversation("Test")
            assert(false) { "Should have thrown exception" }
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Database error") == true)
        }

        // Test setSystemPrompt with repository error
        coEvery { historyRepository.updateSystemPrompt(conversationId, "Prompt") } throws RuntimeException("Database error")

        try {
            interactor.setSystemPrompt(conversationId, "Prompt")
            assert(false) { "Should have thrown exception" }
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Database error") == true)
        }
    }

    @Test
    fun `should handle concurrent error scenarios correctly`() = runTest {
        // Given
        val conversationId = "test-conversationId"
        val apiKey = "test-api-key"

        // Setup multiple error scenarios
        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())
        coEvery { modelsRepository.getChatCompletion(any(), any(), apiKey) } returns
                Result.failure(DomainError.Api(500, "Server Error", "Internal error"))

        // When - Make multiple concurrent requests
        val results = (1..5).map {
            interactor.sendMessage("gpt-3.5-turbo", conversationId)
        }

        // Then - All should handle errors correctly
        results.forEach { flow ->
            flow.test {
                assertEquals(DataResult.Loading, awaitItem())

                val error = awaitItem() as DataResult.Error
                assertTrue(error.exception is DomainError.Api)

                awaitComplete()
            }
        }
    }
}
