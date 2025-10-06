package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class LLMInteractorPerformanceTest {

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
    fun `performance test for large chat history processing`() = runTest {
        // Given
        val conversationId = "performance-test"
        val largeMessageCount = 1000

        val largeHistory = (1..largeMessageCount).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                content = "Message $index with some content to make it realistic"
            )
        }

        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(largeHistory)

        // When
        val executionTime = measureTimeMillis {
            interactor.getChatHistoryFlow(conversationId).collect { messages ->
                assert(messages.size == largeMessageCount)
            }
        }

        // Then
        println("Processing $largeMessageCount messages took $executionTime ms")
        assert(executionTime < 1000) { "Processing took ${executionTime}ms, expected < 1000ms" }
    }

    @Test
    fun `performance test for chat export with large history`() = runTest {
        // Given
        val conversationId = "export-test"
        val messageCount = 500

        val conversation = Conversation(
            id = conversationId,
            title = "Performance Test Chat",
            systemPrompt = "You are a helpful assistant"
        )

        val messages = (1..messageCount).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                content = "Message $index ".repeat(10) // ~100 chars per message
            )
        }

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns messages

        // When
        val executionTime = measureTimeMillis {
            val result = interactor.getFullChatForExport(conversationId)
            assert(result.isNotBlank())
            assert(result.contains("Performance Test Chat"))
        }

        // Then
        println("Exporting $messageCount messages took $executionTime ms")
        assert(executionTime < 2000) { "Export took ${executionTime}ms, expected < 2000ms" }
    }

    @Test
    fun `performance test for multiple file attachments export`() = runTest {
        // Given
        val conversationId = "files-test"
        val fileCount = 50

        val conversation = Conversation(
            id = conversationId,
            title = "Files Test",
            systemPrompt = null
        )

        val files = (1..fileCount).map { index ->
            FileAttachment(
                id = "file-$index",
                fileName = "file$index.txt",
                fileExtension = "txt",
                fileSize = 1024L,
                mimeType = "text/plain",
                originalContent = "Content of file $index"
            )
        }

        val messages = files.map { file ->
            ChatMessage(
                role = ChatRole.USER,
                content = "Process ${file.fileName}",
                fileAttachment = FileAttachmentMetadata(
                    fileId = file.id,
                    fileName = file.fileName,
                    fileExtension = file.fileExtension,
                    fileSize = file.fileSize,
                    mimeType = file.mimeType,
                    preview = file.originalContent.take(100)
                )
            )
        }

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns messages
        files.forEach { file ->
            coEvery { fileRepository.getTemporaryFile(file.id) } returns file
        }

        // When
        val executionTime = measureTimeMillis {
            val result = interactor.getFullChatForExport(conversationId)
            assert(result.contains("## Прикрепленные файлы"))
            files.forEach { file ->
                assert(result.contains(file.fileName))
            }
        }

        // Then
        println("Exporting $fileCount files took $executionTime ms")
        assert(executionTime < 3000) { "Export took ${executionTime}ms, expected < 3000ms" }
    }

    @Test
    fun `memory efficiency test for large file content`() = runTest {
        // Given
        val conversationId = "memory-test"
        val conversation = Conversation(conversationId, "Memory Test", null)
        val largeContent = "A".repeat(100_000) // 100KB content

        val fileAttachment = FileAttachment(
            id = "large-file",
            fileName = "large.txt",
            fileExtension = "txt",
            fileSize = largeContent.length.toLong(),
            mimeType = "text/plain",
            originalContent = largeContent
        )

        val message = ChatMessage(
            role = ChatRole.USER,
            content = "Process this large file",
            fileAttachment = FileAttachmentMetadata(
                fileId = "large-file",
                fileName = "large.txt",
                fileExtension = "txt",
                fileSize = largeContent.length.toLong(),
                mimeType = "text/plain",
                preview = largeContent.take(500)
            )
        )

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns listOf(message)
        coEvery { fileRepository.getTemporaryFile("large-file") } returns fileAttachment

        // When
        val executionTime = measureTimeMillis {
            repeat(10) {
                val result = interactor.getFullChatForExport(conversationId)
                assert(result.contains("large.txt"))
                // File content should be in the export
                assert(result.contains("A".repeat(100)))
            }
        }

        // Then
        println("Processing large file 10 times took $executionTime ms")
        assert(executionTime < 5000) { "Processing took ${executionTime}ms, expected < 5000ms" }
    }

    @Test
    fun `concurrent access performance test`() = runTest {
        // Given
        val conversationCount = 100
        val conversationIds = (1..conversationCount).map { "conversation-$it" }
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "Test message"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "Test response")
        )

        conversationIds.forEach { id ->
            coEvery { historyRepository.getHistoryFlow(id) } returns flowOf(messages)
        }

        // When
        val executionTime = measureTimeMillis {
            conversationIds.forEach { id ->
                interactor.getChatHistoryFlow(id).collect { result ->
                    assert(result.size == 2)
                }
            }
        }

        // Then
        println("Sequential access to $conversationCount conversations took $executionTime ms")
        assert(executionTime < 2000) { "Access took ${executionTime}ms, expected < 2000ms" }
    }

    @Test
    fun `performance test for model list processing with large dataset`() = runTest {
        // Given
        val modelCount = 500
        val models = (1..modelCount).map { index ->
            LlmModel(
                id = "model-$index",
                name = "Test Model $index",
                description = "Description for model $index",
                created = System.currentTimeMillis(),
                contextLength = BigDecimal(4096),
                pricingPrompt = BigDecimal("0.001"),
                pricingCompletion = BigDecimal("0.002"),
                pricingImage = null,
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
                isSelected = index == 1,
                isFavorite = index % 10 == 0
            )
        }

        coEvery { modelsRepository.getModelsFlow() } returns flowOf(models)
        coEvery { settingsRepository.getSelectedModelId() } returns flowOf("model-1")
        coEvery { settingsRepository.getFavoriteModelIds() } returns flowOf(
            models.filter { it.isFavorite }.map { it.id }.toSet()
        )

        // When
        val executionTime = measureTimeMillis {
            interactor.getModels().collect { result ->
                when (result) {
                    is DataResult.Success -> {
                        assert(result.data.size == modelCount)
                        assert(result.data.count { it.isFavorite } == modelCount / 10)
                    }
                    else -> {}
                }
            }
        }

        // Then
        println("Processing $modelCount models took $executionTime ms")
        assert(executionTime < 1000) { "Processing took ${executionTime}ms, expected < 1000ms" }
    }

    @Test
    fun `performance test for system prompt updates with long text`() = runTest {
        // Given
        val conversationId = "prompt-test"
        val longPrompt = "You are a helpful assistant. ".repeat(100) // ~3KB prompt

        coEvery { historyRepository.updateSystemPrompt(conversationId, longPrompt) } returns Unit
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns longPrompt

        // When
        val executionTime = measureTimeMillis {
            repeat(100) {
                interactor.setSystemPrompt(conversationId, longPrompt)
                val result = interactor.getSystemPrompt(conversationId)
                assert(result == longPrompt)
            }
        }

        // Then
        println("Updating system prompt 100 times took $executionTime ms")
        assert(executionTime < 1000) { "Updates took ${executionTime}ms, expected < 1000ms" }
    }

    @Test
    fun `stress test for rapid conversation creation`() = runTest {
        // Given
        val conversationCount = 100
        val conversations = (1..conversationCount).map { index ->
            "conversation-$index"
        }

        conversations.forEachIndexed { index, id ->
            coEvery { historyRepository.createNewConversation("Chat $index") } returns id
        }

        // When
        val executionTime = measureTimeMillis {
            conversations.forEachIndexed { index, _ ->
                val newId = interactor.createNewConversation("Chat $index")
                assert(newId.isNotBlank())
            }
        }

        // Then
        println("Creating $conversationCount conversations took $executionTime ms")
        assert(executionTime < 2000) { "Creation took ${executionTime}ms, expected < 2000ms" }
    }

    @Test
    fun `performance test for message history with mixed content types`() = runTest {
        // Given
        val conversationId = "mixed-test"
        val messageCount = 200

        val messages = (1..messageCount).map { index ->
            when (index % 3) {
                0 -> ChatMessage(
                    role = ChatRole.USER,
                    content = "User message $index with some text content"
                )
                1 -> ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = "Assistant response $index with detailed explanation ".repeat(5)
                )
                else -> ChatMessage(
                    role = ChatRole.USER,
                    content = "File message $index",
                    fileAttachment = FileAttachmentMetadata(
                        fileId = "file-$index",
                        fileName = "file$index.txt",
                        fileExtension = "txt",
                        fileSize = 1024L,
                        mimeType = "text/plain",
                        preview = "Preview of file $index"
                    )
                )
            }
        }

        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)

        // When
        val executionTime = measureTimeMillis {
            interactor.getChatHistoryFlow(conversationId).collect { result ->
                assert(result.size == messageCount)
                val userMessages = result.count { it.role == ChatRole.USER }
                val assistantMessages = result.count { it.role == ChatRole.ASSISTANT }
                val fileMessages = result.count { it.fileAttachment != null }
                assert(userMessages > 0)
                assert(assistantMessages > 0)
                assert(fileMessages > 0)
            }
        }

        // Then
        println("Processing $messageCount mixed messages took $executionTime ms")
        assert(executionTime < 1000) { "Processing took ${executionTime}ms, expected < 1000ms" }
    }

    @Test
    fun `memory usage test for repeated exports`() = runTest {
        // Given
        val conversationId = "memory-export-test"
        val conversation = Conversation(conversationId, "Memory Test", "System prompt")
        val messages = (1..100).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                content = "Message $index with content ".repeat(20)
            )
        }

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns messages

        // When
        val executionTime = measureTimeMillis {
            repeat(50) {
                val result = interactor.getFullChatForExport(conversationId)
                assert(result.isNotBlank())
                // Force GC hint to prevent memory buildup
                System.gc()
            }
        }

        // Then
        println("50 repeated exports took $executionTime ms")
        assert(executionTime < 10000) { "Exports took ${executionTime}ms, expected < 10000ms" }
    }
}
