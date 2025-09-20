import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.presentation.screens.DefaultPromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptDetailEvent
import com.arny.aiprompts.presentation.ui.detail.PromptDetailState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PromptDetailComponentTest {

    private val mockGetPromptUseCase = mockk<GetPromptUseCase>()
    private val mockUpdatePromptUseCase = mockk<UpdatePromptUseCase>()
    private val mockCreatePromptUseCase = mockk<CreatePromptUseCase>()
    private val mockDeletePromptUseCase = mockk<DeletePromptUseCase>()
    private val mockToggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>()
    private val mockGetAvailableTagsUseCase = mockk<GetAvailableTagsUseCase>()

    private val lifecycle = LifecycleRegistry()
    private val componentContext = DefaultComponentContext(lifecycle = lifecycle)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testPrompt = Prompt(
        id = "test-id",
        title = "Test Prompt",
        content = PromptContent(ru = "Test content RU", en = "Test content EN"),
        description = "Test description",
        category = "test",
        tags = listOf("tag1", "tag2"),
        compatibleModels = emptyList(),
        status = "active",
        isLocal = true,
        createdAt = Clock.System.now(),
        modifiedAt = Clock.System.now(),
        isFavorite = false
    )

    @Test
    fun `initial state is loading when prompt exists`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())

        // When
        val component = createComponent("test-id")
        advanceUntilIdle()

        // Then
        val state = component.state.value
        assertFalse(state.isLoading)
        assertEquals(testPrompt, state.prompt)
        assertEquals(emptyList(), state.availableTags)
    }

    @Test
    fun `edit clicked changes state to editing mode`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())

        val component = createComponent("test-id")
        advanceUntilIdle()

        // When
        component.onEvent(PromptDetailEvent.EditClicked)

        // Then
        val state = component.state.value
        assertTrue(state.isEditing)
        assertEquals(testPrompt, state.draftPrompt)
    }

    @Test
    fun `cancel clicked exits editing mode`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())

        val component = createComponent("test-id")
        advanceUntilIdle()
        component.onEvent(PromptDetailEvent.EditClicked)

        // When
        component.onEvent(PromptDetailEvent.CancelClicked)

        // Then
        val state = component.state.value
        assertFalse(state.isEditing)
        assertEquals(null, state.draftPrompt)
    }

    @Test
    fun `save clicked calls update use case for existing prompt`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())
        coEvery { mockUpdatePromptUseCase(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val component = createComponent("test-id")
        advanceUntilIdle()
        component.onEvent(PromptDetailEvent.EditClicked)

        // When
        component.onEvent(PromptDetailEvent.SaveClicked)
        advanceUntilIdle()

        // Then
        coVerify { mockUpdatePromptUseCase(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `delete clicked calls delete use case and navigates back for local prompt`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())
        coEvery { mockDeletePromptUseCase("test-id") } returns Result.success(Unit)

        var navigatedBack = false
        val component = createComponent("test-id") { navigatedBack = true }
        advanceUntilIdle()

        // When
        component.onEvent(PromptDetailEvent.DeleteClicked)
        advanceUntilIdle()

        // Then
        coVerify { mockDeletePromptUseCase("test-id") }
        assertTrue(navigatedBack)
    }

    @Test
    fun `favorite clicked calls toggle favorite use case`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())
        coEvery { mockToggleFavoriteUseCase("test-id") } returns Unit
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt.copy(isFavorite = true)))

        val component = createComponent("test-id")
        advanceUntilIdle()

        // When
        component.onEvent(PromptDetailEvent.FavoriteClicked)
        advanceUntilIdle()

        // Then
        coVerify { mockToggleFavoriteUseCase("test-id") }
    }

    @Test
    fun `new prompt creation initializes in editing mode`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("new-id") } returns flowOf(Result.success(null))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(listOf("tag1", "tag2"))

        // When
        val component = createComponent("new-id")
        advanceUntilIdle()

        // Then
        val state = component.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isEditing)
        assertEquals("new-id", state.draftPrompt?.id)
        assertEquals("", state.draftPrompt?.title)
        assertEquals(listOf("tag1", "tag2"), state.availableTags)
    }

    @Test
    fun `save new prompt calls create use case`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("new-id") } returns flowOf(Result.success(null))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())
        coEvery { mockCreatePromptUseCase(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(123L)

        val component = createComponent("new-id")
        advanceUntilIdle()

        // When
        component.onEvent(PromptDetailEvent.SaveClicked)
        advanceUntilIdle()

        // Then
        coVerify { mockCreatePromptUseCase(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `title changed updates draft prompt title`() = runTest {
        // Given
        coEvery { mockGetPromptUseCase.getPromptFlow("test-id") } returns flowOf(Result.success(testPrompt))
        coEvery { mockGetAvailableTagsUseCase() } returns Result.success(emptyList())

        val component = createComponent("test-id")
        advanceUntilIdle()
        component.onEvent(PromptDetailEvent.EditClicked)

        // When
        component.onEvent(PromptDetailEvent.TitleChanged("New Title"))

        // Then
        val state = component.state.value
        assertEquals("New Title", state.draftPrompt?.title)
    }

    private fun createComponent(
        promptId: String,
        onNavigateBack: () -> Unit = {}
    ): DefaultPromptDetailComponent {
        lifecycle.resume()
        return DefaultPromptDetailComponent(
            componentContext = componentContext,
            getPromptUseCase = mockGetPromptUseCase,
            updatePromptUseCase = mockUpdatePromptUseCase,
            createPromptUseCase = mockCreatePromptUseCase,
            deletePromptUseCase = mockDeletePromptUseCase,
            toggleFavoriteUseCase = mockToggleFavoriteUseCase,
            getAvailableTagsUseCase = mockGetAvailableTagsUseCase,
            promptId = promptId,
            onNavigateBack = onNavigateBack
        )
    }
}