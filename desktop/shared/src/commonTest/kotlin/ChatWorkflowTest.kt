@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import com.arny.aiprompts.data.model.*
import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.domain.files.PlatformFileHandler
import com.arny.aiprompts.domain.interactors.LLMInteractor
import com.arny.promptcontract.ProviderConfig
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ChatWorkflowTest {
    private val api = mockk<IOpenRouterRepository>()
    private val settings = mockk<ISettingsRepository>()
    private val repository = mockk<IChatSessionRepository>()
    private val fileHandler = mockk<PlatformFileHandler>()
    private val history = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val session = ChatSession("chat", "Test", "System instructions", ChatSettings(0.25f, 123, 0.8f, 3), 1, 1)
    private val interactor = LLMInteractor(api, settings, repository, mockk(), fileHandler)

    init {
        every { settings.getOpenRouterApiKey() } returns "test-key"
        every { settings.loadProviders() } returns ProviderConfig()
        every { settings.getSelectedModelId() } returns flowOf("test-model")
        every { settings.getUserContext() } returns ""
        every { repository.getAllSessions() } returns flowOf(listOf(session))
        every { repository.getSessionById("chat") } returns flowOf(session)
        every { repository.getMessagesForSession("chat") } returns history
        coEvery { repository.addMessage("chat", any()) } coAnswers {
            history.value = history.value + secondArg<ChatMessage>(); Unit
        }
        coEvery { repository.updateMessage(any()) } coAnswers {
            val message = firstArg<ChatMessage>()
            history.value = history.value.map { if (it.id == message.id) message else it }; Unit
        }
        coEvery { repository.deleteMessage(any()) } coAnswers {
            history.value = history.value.filterNot { it.id == firstArg<String>() }; Unit
        }
        coEvery { repository.getRecentMessagesForContext("chat", any()) } coAnswers { history.value.takeLast(secondArg()) }
    }

    private fun stream(flow: Flow<Result<StreamingChatChunk>>) {
        every { api.getStreamingChatCompletion(any(), any(), any(), any(), any(), any()) } returns flow
    }
    private fun successful() = flowOf(Result.success(StreamingChatChunk("Answer", isComplete = true)))

    @Test fun `send persists user and completed answer and forwards session settings`() = runTest {
        stream(successful())
        interactor.sendMessage("chat", "Question").toList()
        assertEquals(listOf("Question", "Answer"), history.value.map { it.content })
        assertTrue(history.value.all { it.status == MessageStatus.Sent })
        verify { api.getStreamingChatCompletion("test-model", match {
            it.first().content == "System instructions" && it.last().content == "Question" && it.none { m -> m.isStreaming() }
        }, null, session.settings.temperature.toDouble(), 123, session.settings.topP.toDouble()) }
    }

    @Test fun `incomplete and length limited streams retain partial text with failure status`() = runTest {
        for (chunk in listOf(StreamingChatChunk("Partial"), StreamingChatChunk("Partial", "length", true))) {
            history.value = emptyList()
            stream(flowOf(Result.success(chunk)))
            interactor.sendMessage("chat", "Question").toList()
            assertEquals("Partial", history.value.last().content)
            assertTrue(history.value.last().status is MessageStatus.Failed)
        }
    }

    @Test fun `retry replaces response without duplicating user message`() = runTest {
        stream(successful())
        interactor.sendMessage("chat", "Question").toList()
        val responseId = history.value.last().id
        interactor.retryMessage(responseId)
        assertEquals(2, history.value.size)
        assertEquals(1, history.value.count { it.role == ChatMessageRole.USER })
        assertNotEquals(responseId, history.value.last().id)
    }

    @Test fun `editing preserves message id and versions and regenerates later answers`() = runTest {
        stream(successful())
        interactor.sendMessage("chat", "Original").toList()
        val id = history.value.first().id
        interactor.editMessage(id, "Changed").toList()
        assertEquals(2, history.value.size)
        assertEquals(id, history.value.first().id)
        assertEquals("Changed", history.value.first().content)
        assertEquals(listOf("Original"), history.value.first().previousVersions)
    }

    @Test fun `provider failure removes empty placeholder and preserves user question`() = runTest {
        stream(flowOf(Result.failure(IllegalStateException("Provider unavailable"))))
        interactor.sendMessage("chat", "Question").toList()
        assertEquals(listOf("Question"), history.value.map { it.content })
    }

    @Test fun `stop preserves partial content without leaving streaming state`() = runTest {
        stream(flow {
            emit(Result.success(StreamingChatChunk("Partial")))
            awaitCancellation()
        })
        val job = launch { interactor.sendMessage("chat", "Question").collect() }
        runCurrent()
        job.cancelAndJoin()
        assertEquals("Partial", history.value.last().content)
        assertFalse(history.value.any { it.isStreaming() })
        stream(successful())
        interactor.sendMessage("chat", "Next").toList()
        assertEquals("Answer", history.value.last().content)
    }

    @Test fun `stop before first token removes placeholder`() = runTest {
        stream(flow { awaitCancellation() })
        val job = launch { interactor.sendMessage("chat", "Question").collect() }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, history.value.size)
        assertEquals(ChatMessageRole.USER, history.value.single().role)
    }

    @Test fun `text attachment alone is saved and sent in API context`() = runTest {
        stream(successful())
        coEvery { fileHandler.copyToInternalStorage("source.txt", any()) } returns "internal.txt"
        every { fileHandler.getFileSize("source.txt") } returns 12
        coEvery { fileHandler.readText("internal.txt") } returns "File content"
        interactor.sendMessageWithAttachments("chat", com.arny.aiprompts.domain.interactors.MessageInput("",
            listOf(com.arny.aiprompts.domain.interactors.AttachmentInput("source.txt", "source.txt", "text/plain")))).toList()
        assertTrue(history.value.first().content.contains("File content"))
        assertEquals("internal.txt", history.value.first().attachments.single().uri)
        verify { api.getStreamingChatCompletion(any(), match { it.last().content.contains("File content") }, any(), any(), any(), any()) }
    }

    @Test fun `unreadable attachment fails before saving user message`() = runTest {
        coEvery { fileHandler.copyToInternalStorage(any(), any()) } throws java.io.IOException("File removed")
        val result = interactor.sendMessageWithAttachments("chat", com.arny.aiprompts.domain.interactors.MessageInput("Question",
            listOf(com.arny.aiprompts.domain.interactors.AttachmentInput("missing.txt", "missing.txt", "text/plain")))).toList()
        assertTrue(result.last() is com.arny.aiprompts.results.DataResult.Error)
        assertTrue(history.value.isEmpty())
        verify { api wasNot Called }
    }
}
