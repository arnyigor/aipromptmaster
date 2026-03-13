package com.arny.aipromptmaster.domain.interactors

/*
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class LLMInteractorTest {

    private val openRouterRepository = mockk<IOpenRouterRepository>(relaxed = true)
    private val settingsRepository = mockk<ISettingsRepository>(relaxed = true)
    private val historyRepo = mockk<IChatHistoryRepository>(relaxed = true)
    private val fileRepo = mockk<IFileRepository>(relaxed = true)
    private val modelRepository = mockk<ModelRepository>(relaxed = true)

    private fun createInteractor(): LLMInteractor =
        LLMInteractor(
            routerRepository = openRouterRepository,
            settingsRepository = settingsRepository,
            historyRepository = historyRepo,
            fileRepository = fileRepo,
            modelRepository = modelRepository
        )

    @Test
    fun `sendMessageWithFallback should add user and assistant messages when no files`() = runTest {
        val interactor = createInteractor()
        coEvery { historyRepo.getConversation("conv1") } returns Conversation(
            id = "conv1",
            title = "test",
            systemPrompt = null
        )
        every { historyRepo.addMessage(any(), any()) } returns "msgId"

        interactor.sendMessageWithFallback(
            conversationId = "conv1",
            userMessageText = "Hello world"
        )

        val slot = slot<ChatMessage>()
        verifySequence {
            historyRepo.addMessage("conv1", capture(slot))
            historyRepo.addMessage(any(), match { it.content.isEmpty() })
        }
        assertEquals(ChatRole.USER, slot.captured.role)
        assertEquals("Hello world", slot.captured.content)
    }

    @Test
    fun `sendMessageWithFallback creates new conversation when missing`() = runTest {
        val interactor = createInteractor()

        coEvery { historyRepo.getConversation(any()) } returns null
        every { historyRepo.createNewConversation("Hello world") } returns "conv2"
        every { historyRepo.addMessage(any(), any()) } returns "msgId"

        interactor.sendMessageWithFallback(
            conversationId = null,
            userMessageText = "Hi"
        )

        verifySequence {
            historyRepo.createNewConversation("Hello world")
            historyRepo.addMessage(any(), any())
        }
    }

    @Test
    fun `sendMessageWithFallback handles multiple attached files`() = runTest {
        val interactor = createInteractor()
        coEvery { historyRepo.getConversation(any()) } returns Conversation(
            id = "conv1",
            title = "",
            systemPrompt = null
        )
        every { fileRepo.saveTemporaryFile(any()) } answers { firstArg<FileAttachment>() }
        every { historyRepo.addMessage(any(), any()) } returns "msgId"
        val files = listOf(
            FileAttachment("f1", "file.txt", "txt", 100, "text/plain", "content1"),
            FileAttachment("f2", "file2.kt", "kt", 200, "text/x-kotlin", "code")
        )

        coEvery {
            openRouterRepository.getChatCompletionStreamWithFiles(
                any(),
                any()
            )
        } returns emptyFlow()

        interactor.sendMessageWithFallback(
            conversationId = "conv1",
            userMessageText = "msg",
            attachedFiles = files
        )

        verifySequence {
            fileRepo.saveTemporaryFile("f1")
            fileRepo.saveTemporaryFile("f2")
            historyRepo.addMessage(any(), match { it.fileAttachment?.fileId == "f1" })
            repeat(files.size - 1) {
                historyRepo.addMessage(
                    any(),
                    match { it.content.isEmpty() && it.fileAttachment?.fileId != null })
            }
        }
    }

    @Test
    fun `sendMessageWithFallback deletes assistant message on stream error`() = runTest {
        val interactor = createInteractor()
        coEvery { historyRepo.getConversation(any()) } returns Conversation(
            id = "c1",
            title = "",
            systemPrompt = null
        )
        every { historyRepo.addMessage(any(), any()) } returns "assistantMsg"

        val error = DomainError.Generic("API error")
        coEvery {
            openRouterRepository.getChatCompletionStreamWithFiles(
                any(),
                any()
            )
        } returns flowOf(DataResult.Error(error))

        interactor.sendMessageWithFallback(
            conversationId = "c1",
            userMessageText = "test"
        )

        verify { historyRepo.deleteMessage("assistantMsg") }
    }

    @Test
    fun `streaming buffer updates correctly`() = runTest {
        val interactor = createInteractor()
        coEvery { historyRepo.getConversation(any()) } returns Conversation(
            id = "c1",
            title = "",
            systemPrompt = null
        )
        every { historyRepo.addMessage(any(), any()) } answers { "msgId" }

        val chunks = listOf("Hello ", "world", "!")

        coEvery {
            openRouterRepository.getChatCompletionStreamWithFiles(
                any(),
                any()
            )
        } returns flow {
            chunks.forEach { emit(DataResult.Success(it)) }
        }

        interactor.sendMessageWithFallback(
            conversationId = "c1",
            userMessageText = "ignored"
        )

        val finalBuffer = interactor.observeStreamingBuffer().value["msgId"]
        assertEquals("Hello world!", finalBuffer)
    }

    @Test
    fun `buildMessagesForApi includes system prompt only if absent`() = runTest {
        val interactor = createInteractor()

        val msgs = listOf(
            ChatMessage(id = "m1", role = ChatRole.USER, content = "Hi")
        )
        coEvery { historyRepo.getHistoryFlow(any()).first() } returns msgs
        every { historyRepo.getSystemPrompt(any()) } returns "system text"

        val payload = interactor.buildMessagesForApi("c1", "system text")

        assertEquals(2, payload.messages.size)
        assertTrue(payload.messages.any { it.role == "system" })
    }

    @Test
    fun `buildMessagesForApi skips system prompt when already present`() = runTest {
        val interactor = createInteractor()
        // System message in history
        val msgs = listOf(
            ChatMessage(id = "s1", role = ChatRole.SYSTEM, content = "system"),
            ChatMessage(id = "u1", role = ChatRole.USER, content = "hi")
        )
        coEvery { historyRepo.getHistoryFlow(any()).first() } returns msgs
        every { historyRepo.getSystemPrompt(any()) } returns "system"

        val payload = interactor.buildMessagesForApi("c1", "system")

        assertEquals(2, payload.messages.size)
        assertFalse(payload.messages.any { it.role == "system" && it.content == "system" })
    }

    @Test
    fun `estimateTokens calculates correctly`() = runTest {
        val interactor = createInteractor()
        val text = "a".repeat(10) // 10 chars -> ceil(10/2.5)=4
        val files = listOf(
            FileAttachment("f1", "txt", "txt", 100, "text/plain", "b".repeat(50))
        )
        val systemPrompt = "s".repeat(7) // 7 chars -> ceil(7/2.5)=3

        val tokens = interactor.estimateTokens(text, files, systemPrompt, emptyList())

        assertEquals(27, tokens)
    }

    @Test
    fun `getChatList forwards flow from repository`() = runTest {
        val interactor = createInteractor()
        val listFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { historyRepo.getChatList() } returns listFlow

        val observed = mutableListOf<List<Chat>>()
        val job = launch {
            interactor.getChatList().collect { observed.add(it) }
        }

        listFlow.value = listOf(Chat(id = "c1", name = "Test", lastUpdated = 0))
        assertEquals(2, observed.size)
        assertTrue(observed.last()!!.any { it.id == "c1" })
        job.cancel()
    }
}
*/
