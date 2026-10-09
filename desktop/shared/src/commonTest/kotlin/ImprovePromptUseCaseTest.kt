@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import com.arny.aiprompts.data.model.*
import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.domain.model.*
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.presentation.ui.detail.*
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ImprovePromptUseCaseTest {
    private val repository = mockk<IOpenRouterRepository>()
    private val settings = mockk<ISettingsRepository> {
        every { getSelectedModelId() } returns flowOf("local-model")
    }
    private val useCase = ImprovePromptUseCase(repository, settings)
    private fun stream(chunks: Flow<Result<StreamingChatChunk>>) {
        every { repository.getStreamingChatCompletion(any(), any(), any(), any(), any(), any()) } returns chunks
    }
    private fun prompt() = Prompt("id", "Title", null, PromptContent(ru = "Исходный", en = "Original"), compatibleModels = emptyList(), category = "general", status = "active", createdAt = null, modifiedAt = null)

    @Test fun `stream preserves source and hides reasoning`() = runTest {
        stream(flowOf(Result.success(StreamingChatChunk("<think>private</think>Better ")), Result.success(StreamingChatChunk("{name}", isComplete = true))))
        val result = useCase(PromptImprovementRequest("Original {name}", "local-model", temperature = 0.3, maxTokens = 2048)).toList()
        assertEquals("Better {name}", result.last())
        assertTrue(result.none { "private" in it })
        verify { repository.getStreamingChatCompletion("local-model", match { it.last().content == "Original {name}" && it.first().role == ChatMessageRole.SYSTEM }, null, 0.3, 2048) }
    }

    @Test fun `non streaming response is supported`() = runTest {
        coEvery { repository.getChatCompletion(any(), any(), any(), any(), any()) } returns Result.success(ChatCompletionResponse(choices = listOf(Choice(ChatMessage(role = ChatMessageRole.MODEL, content = "Improved")))))
        assertEquals("Improved", useCase(PromptImprovementRequest("Original", "model", stream = false)).single())
    }

    @Test fun `empty input is rejected before network request`() = runTest {
        assertFailsWith<IllegalArgumentException> { useCase(PromptImprovementRequest("", "model")).toList() }
        verify { repository wasNot Called }
    }

    @Test fun `truncated and disconnected streams cannot complete`() = runTest {
        stream(flowOf(Result.success(StreamingChatChunk("Partial", finishReason = "length"))))
        assertFailsWith<IllegalArgumentException> { useCase(PromptImprovementRequest("Original", "model")).toList() }
        stream(flowOf(Result.success(StreamingChatChunk("Partial"))))
        assertFailsWith<IllegalArgumentException> { useCase(PromptImprovementRequest("Original", "model")).toList() }
    }

    @Test fun `cancelled preview cannot apply`() = runTest {
        stream(flow { emit(Result.success(StreamingChatChunk("Partial"))); awaitCancellation() })
        var applied = false
        val controller = PromptImprovementController(useCase, this) { _, _ -> applied = true }
        controller.open(prompt()); runCurrent()
        controller.onAction(PromptImprovementAction.Generate); runCurrent()
        assertEquals("Partial", controller.state.value.result)
        controller.onAction(PromptImprovementAction.Cancel)
        controller.onAction(PromptImprovementAction.Apply); runCurrent()
        assertFalse(applied)
        assertFalse(controller.state.value.canApply)
        assertFalse(controller.state.value.running)
    }

    @Test fun `completed result applies only explicitly to selected language`() = runTest {
        stream(flowOf(Result.success(StreamingChatChunk("Improved", isComplete = true))))
        var applied: Pair<PromptLanguage, String>? = null
        val controller = PromptImprovementController(useCase, this) { language, text -> applied = language to text }
        controller.open(prompt()); runCurrent()
        controller.onAction(PromptImprovementAction.Language(PromptLanguage.EN))
        assertEquals("Original", controller.state.value.source)
        controller.onAction(PromptImprovementAction.Generate); runCurrent()
        assertNull(applied)
        assertTrue(controller.state.value.canApply)
        controller.onAction(PromptImprovementAction.Apply)
        assertEquals(PromptLanguage.EN to "Improved", applied)
        assertFalse(controller.state.value.visible)
    }
}
