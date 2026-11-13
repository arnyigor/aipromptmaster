package com.arny.aipromptmaster

import androidx.lifecycle.SavedStateHandle
import com.arny.aipromptmaster.domain.interactors.*
import com.arny.aipromptmaster.domain.models.*
import com.arny.aipromptmaster.domain.repositories.*
import com.arny.aipromptmaster.ui.screens.systemprompt.SystemPromptViewModel
import com.arny.promptcontract.ChatGenerationConfig
import com.arny.sharedui.GenerationParametersUi
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatGenerationSettingsTest {
    @Test fun settingsAndUnsavedPromptRestoreThenSaveForTheSameConversation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val interactor = mockk<ILLMInteractor>(relaxed = true)
            every { interactor.getChatGeneration("chat") } returns ChatGenerationConfig()
            coEvery { interactor.getSystemPrompt("chat") } returns "Stored prompt"
            val handle = SavedStateHandle()
            val first = SystemPromptViewModel("chat", interactor, handle)
            first.onTextChanged("Unsaved prompt")
            first.onGenerationChanged(GenerationParametersUi(1.2f, 4096, 0.6f, 4))
            runCurrent()
            val second = SystemPromptViewModel("chat", interactor, SavedStateHandle(mapOf(
                "system-prompt" to handle.get<String>("system-prompt"), "chat-generation" to handle.get<String>("chat-generation"))))
            runCurrent()
            assertEquals("Unsaved prompt", second.systemPrompt.value)
            assertEquals(ChatGenerationConfig(1.2f, 4096, 0.6f, 4), second.generation.value)
            second.onSaveClicked(); runCurrent()
            coVerify { interactor.setSystemPrompt("chat", "Unsaved prompt") }
            verify { interactor.saveChatGeneration("chat", ChatGenerationConfig(1.2f, 4096, 0.6f, 4)) }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun streamingUsesSavedParametersCurrentSystemPromptAndBoundedHistoryWithoutPlaceholder() = runTest {
        val router = mockk<IOpenRouterRepository>(relaxed = true)
        val models = mockk<ModelRepository>(); val settings = mockk<ISettingsRepository>()
        val history = mockk<IChatHistoryRepository>(relaxed = true)
        val selected = mockk<LlmModel>(); every { selected.id } returns "model"; every { selected.contextLength } returns "8192"
        coEvery { models.getSelectedModel() } returns selected
        every { settings.getApiKey() } returns "fake-test-key"
        val generation = ChatGenerationConfig(1.1f, 3072, 0.8f, 2)
        every { settings.getChatGeneration("chat") } returns generation
        coEvery { history.addMessage(any(), any()) } returns "placeholder"
        coEvery { history.getSystemPrompt("chat") } returns "Current system"
        coEvery { history.getFullHistory("chat") } returns listOf(
            ChatMessage(id = "old-system", role = ChatRole.SYSTEM, content = "Old system"),
            ChatMessage(id = "old-user", role = ChatRole.USER, content = "Old user"),
            ChatMessage(id = "answer", role = ChatRole.ASSISTANT, content = "Previous answer"),
            ChatMessage(id = "user", role = ChatRole.USER, content = "New question"),
            ChatMessage(id = "placeholder", role = ChatRole.ASSISTANT, content = "Думает..."))
        val captured = slot<List<ChatMessage>>()
        coEvery { router.getChatCompletionStream("model", capture(captured), "fake-test-key", emptyList(), selected, generation) } returns emptyFlow()
        val interactor = LLMInteractor(router, models, settings, history, mockk(relaxed = true))
        interactor.sendMessageWithFallback("chat", "New question", emptyList(), true)
        assertEquals(listOf("Current system", "Previous answer", "New question"), captured.captured.map { it.content })
        assertEquals(ChatRole.SYSTEM, captured.captured.first().role)
    }
    @Test fun interruptedStreamKeepsPartialAnswerInsteadOfDeletingIt() = runTest {
        val router = mockk<IOpenRouterRepository>(relaxed = true)
        val models = mockk<ModelRepository>(); val settings = mockk<ISettingsRepository>()
        val history = mockk<IChatHistoryRepository>(relaxed = true)
        val selected = mockk<LlmModel>()
        every { selected.id } returns "model"; every { selected.contextLength } returns "8192"
        coEvery { models.getSelectedModel() } returns selected
        every { settings.getApiKey() } returns "fake-test-key"
        every { settings.getChatGeneration("chat") } returns ChatGenerationConfig()
        coEvery { history.addMessage(any(), any()) } returns "placeholder"
        coEvery { history.getSystemPrompt("chat") } returns ""
        coEvery { history.getFullHistory("chat") } returns listOf(ChatMessage(id="user", role=ChatRole.USER,content="Question"))
        coEvery { router.getChatCompletionStream(any(),any(),any(),any(),any(),any()) } returns kotlinx.coroutines.flow.flow {
            emit(DataResult.Success(StreamResult("Partial", "model")))
            throw IllegalStateException("Disconnected")
        }
        val interactor = LLMInteractor(router, models, settings, history, mockk(relaxed = true))
        assertFailsWith<IllegalStateException> { interactor.sendMessageWithFallback("chat", "Question",emptyList(),true) }
        coVerify { history.updateMessageContent("placeholder", match { it.startsWith("Partial") && it.contains("Ответ прерван") }) }
        coVerify(exactly=0) { history.deleteMessage("placeholder") }
    }

}
