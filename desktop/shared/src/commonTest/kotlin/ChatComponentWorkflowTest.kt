@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arny.aiprompts.data.model.*
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.presentation.features.llm.DefaultLlmComponent
import com.arny.aiprompts.results.DataResult
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ChatComponentWorkflowTest {
    private fun session(id: String) = ChatSession(id, id, null, ChatSettings(), 0, 0)
    private fun interactor(sessions: MutableStateFlow<List<ChatSession>>) = mockk<ILLMInteractor>(relaxed = true) {
        every { getAllSessions() } returns sessions
        every { getModels() } returns flowOf(DataResult.Success(listOf(LlmModel("model", "Model", "", 0, null, null, null, null, emptyList(), emptyList(), true))))
        every { getMessagesForSession(any()) } returns MutableStateFlow(emptyList())
        coEvery { refreshModels() } returns Result.success(Unit)
    }

    @Test fun `per chat drafts survive switching and recreation`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val first = LifecycleRegistry()
        val second = LifecycleRegistry()
        try {
            val sessions = MutableStateFlow(listOf(session("one"), session("two")))
            val llm = interactor(sessions)
            val keeper = StateKeeperDispatcher()
            val component = DefaultLlmComponent(DefaultComponentContext(first, stateKeeper = keeper), llm, {})
            component.onPromptChanged("Draft one")
            component.onAttachmentsAdded(listOf(com.arny.aiprompts.domain.interactors.AttachmentInput("one.txt", "one.txt", "text/plain")))
            component.onChatSessionSelected("two")
            assertEquals("", component.uiState.value.prompt)
            assertTrue(component.uiState.value.attachments.isEmpty())
            component.onPromptChanged("Draft two")
            component.onChatSessionSelected("one")
            assertEquals("Draft one", component.uiState.value.prompt)
            assertEquals("one.txt", component.uiState.value.attachments.single().uri)
            val snapshot = keeper.save()
            first.destroy()
            val restored = DefaultLlmComponent(DefaultComponentContext(second, stateKeeper = StateKeeperDispatcher(snapshot)), llm, {})
            assertEquals("Draft one", restored.uiState.value.prompt)
            restored.onChatSessionSelected("two")
            assertEquals("Draft two", restored.uiState.value.prompt)
        } finally { first.destroy(); second.destroy(); Dispatchers.resetMain() }
    }

    @Test fun `first send creates chat and duplicate clicks cannot send twice`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry()
        try {
            val sessions = MutableStateFlow<List<ChatSession>>(emptyList())
            val llm = interactor(sessions)
            coEvery { llm.createSession(any(), any()) } coAnswers { session("new").also { sessions.value = listOf(it) } }
            every { llm.sendMessage("new", "Hello") } returns flow {
                emit(DataResult.Success(ChatMessage(role = ChatMessageRole.MODEL, content = "", status = MessageStatus.Streaming(false))))
                awaitCancellation()
            }
            val component = DefaultLlmComponent(DefaultComponentContext(lifecycle), llm, {})
            component.onSystemPromptChanged("Be concise")
            component.onChatSettingsChanged(ChatSettings(maxTokens = 123))
            component.onPromptChanged("Hello")
            component.onStreamingGenerateClicked()
            assertTrue(component.uiState.value.isGenerating)
            component.onStreamingGenerateClicked()
            verify(exactly = 1) { llm.sendMessage("new", "Hello") }
            coVerify { llm.createSession("Hello", "Be concise") }
            coVerify { llm.updateChatSettings("new", ChatSettings(maxTokens = 123)) }
            assertEquals("new", component.uiState.value.selectedChatId)
            assertEquals("", component.uiState.value.prompt)
            component.onCancelGenerating()
            runCurrent()
            assertFalse(component.uiState.value.isGenerating)
        } finally { lifecycle.destroy(); Dispatchers.resetMain() }
    }
}
