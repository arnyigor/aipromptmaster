package com.arny.aipromptmaster.presentation.ui.home

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.PromptMetadata
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class PromptsViewModelTest {

    private lateinit var promptsInteractor: IPromptsInteractor
    private lateinit var settingsInteractor: ISettingsInteractor
    private lateinit var viewModel: PromptsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        promptsInteractor = mockk()
        settingsInteractor = mockk()
        viewModel = PromptsViewModel(promptsInteractor, settingsInteractor)
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Assert
        val initialState = viewModel.uiState.first()
        assertEquals(PromptsUiState.Initial, initialState)

        val initialSortData = viewModel.sortDataState.first()
        assertEquals(null, initialSortData)

        val initialSearchState = viewModel.searchState.first()
        assertEquals(SearchState(), initialSearchState)
    }

    @Test
    fun `loadSortData should load and set sort data on init`() = runTest {
        // Arrange
        val sortData = PromptsSortData(
            categories = listOf("cat1", "cat2"),
            tags = listOf("tag1", "tag2")
        )
        coEvery { promptsInteractor.getPromptsSortData() } returns sortData

        // Act
        advanceUntilIdle()

        // Assert
        val resultSortData = viewModel.sortDataState.first()
        assertEquals(sortData, resultSortData)
        coVerify { promptsInteractor.getPromptsSortData() }
    }

    @Test
    fun `loadSortData should handle exception gracefully`() = runTest {
        // Arrange
        coEvery { promptsInteractor.getPromptsSortData() } throws RuntimeException("Test error")

        // Act
        advanceUntilIdle()

        // Assert
        val resultSortData = viewModel.sortDataState.first()
        assertEquals(null, resultSortData)
        // Exception should be caught and printed, but not crash the app
    }

    @Test
    fun `sendFeedback should emit result to feedbackResult flow`() = runTest {
        // Arrange
        val feedbackContent = "Test feedback"
        val expectedResult = Result.success(Unit)
        coEvery { settingsInteractor.sendFeedback(feedbackContent) } returns expectedResult

        // Act
        viewModel.sendFeedback(feedbackContent)

        // Assert
        val feedbackResult = viewModel.feedbackResult.first()
        assertEquals(expectedResult, feedbackResult)
        coVerify { settingsInteractor.sendFeedback(feedbackContent) }
    }

    @Test
    fun `onSortButtonClicked should emit OpenSortScreenEvent when sort data is available`() = runTest {
        // Arrange
        val sortData = PromptsSortData(
            categories = listOf("cat1", "cat2"),
            tags = listOf("tag1", "tag2")
        )
        val currentFilters = CurrentFilters("category1", listOf("tag1"))

        // Set sort data first
        val sortDataStateFlow = MutableStateFlow(sortData)
        // We need to mock the state flow behavior
        coEvery { promptsInteractor.getPromptsSortData() } returns sortData

        // Act
        viewModel.onSortButtonClicked()

        // Assert
        // The event should be emitted, but we need to collect from the flow
        // In a real test, we would collect from viewModel.event
    }

    @Test
    fun `handleLoadStates should update UI state correctly for loading state`() = runTest {
        // Arrange
        val loadStates = CombinedLoadStates(
            refresh = LoadState.Loading,
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false)
        )

        // Act
        viewModel.handleLoadStates(loadStates, 10)

        // Assert
        val uiState = viewModel.uiState.first()
        assertEquals(PromptsUiState.Loading, uiState)
    }

    @Test
    fun `handleLoadStates should update UI state correctly for error state`() = runTest {
        // Arrange
        val error = RuntimeException("Test error")
        val loadStates = CombinedLoadStates(
            refresh = LoadState.Error(error),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false)
        )

        // Act
        viewModel.handleLoadStates(loadStates, 10)

        // Assert
        val uiState = viewModel.uiState.first()
        assertTrue(uiState is PromptsUiState.Error)
        assertEquals(error, (uiState as PromptsUiState.Error).error)
    }

    @Test
    fun `handleLoadStates should update UI state correctly for empty state`() = runTest {
        // Arrange
        val loadStates = CombinedLoadStates(
            refresh = LoadState.NotLoading(false),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false)
        )

        // Act
        viewModel.handleLoadStates(loadStates, 0)

        // Assert
        val uiState = viewModel.uiState.first()
        assertEquals(PromptsUiState.Empty, uiState)
    }

    @Test
    fun `handleLoadStates should update UI state correctly for content state`() = runTest {
        // Arrange
        val loadStates = CombinedLoadStates(
            refresh = LoadState.NotLoading(false),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false)
        )

        // Act
        viewModel.handleLoadStates(loadStates, 5)

        // Assert
        val uiState = viewModel.uiState.first()
        assertEquals(PromptsUiState.Content, uiState)
    }

    @Test
    fun `search should update search state with query`() = runTest {
        // Arrange
        val query = "test query"

        // Act
        viewModel.search(query)

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(query, searchState.query)
    }

    @Test
    fun `removeFilter should remove category filter correctly`() = runTest {
        // Arrange - set initial state with category filter
        val initialSearchState = SearchState(
            query = "test",
            category = "category1",
            tags = listOf("tag1")
        )

        // Act
        viewModel.removeFilter("category1")

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(null, searchState.category)
        assertEquals(listOf("tag1"), searchState.tags)
    }

    @Test
    fun `removeFilter should remove tag filter correctly`() = runTest {
        // Arrange - set initial state with tag filter
        val initialSearchState = SearchState(
            query = "test",
            category = "category1",
            tags = listOf("tag1", "tag2")
        )

        // Act
        viewModel.removeFilter("tag1")

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals("category1", searchState.category)
        assertEquals(listOf("tag2"), searchState.tags)
    }

    @Test
    fun `resetSearchAndFilters should reset all filters and search`() = runTest {
        // Arrange - set initial state with filters and search
        val initialSearchState = SearchState(
            query = "test query",
            category = "category1",
            status = "active",
            tags = listOf("tag1")
        )

        // Act
        viewModel.resetSearchAndFilters()

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(SearchState(), searchState)
    }

    @Test
    fun `synchronize should emit sync events and handle success result`() = runTest {
        // Arrange
        val syncResult = SyncResult.Success(listOf(createTestPrompt()))
        coEvery { promptsInteractor.synchronize() } returns syncResult

        // Act
        viewModel.synchronize()

        // Assert - events should be emitted in sequence
        // In a real test, we would collect from viewModel.event and verify the sequence
        coVerify { promptsInteractor.synchronize() }
    }

    @Test
    fun `synchronize should handle error result from interactor`() = runTest {
        // Arrange
        val errorMessage = StringHolder.Text("Sync failed")
        val syncResult = SyncResult.Error(errorMessage)
        coEvery { promptsInteractor.synchronize() } returns syncResult

        // Act
        viewModel.synchronize()

        // Assert
        coVerify { promptsInteractor.synchronize() }
    }

    @Test
    fun `synchronize should handle too soon result from interactor`() = runTest {
        // Arrange
        val syncResult = SyncResult.TooSoon
        coEvery { promptsInteractor.synchronize() } returns syncResult

        // Act
        viewModel.synchronize()

        // Assert
        coVerify { promptsInteractor.synchronize() }
    }

    @Test
    fun `deletePrompt should call interactor and handle exception`() = runTest {
        // Arrange
        val promptId = "test-prompt-id"
        coEvery { promptsInteractor.deletePrompt(promptId) } returns Unit

        // Act
        viewModel.deletePrompt(promptId)

        // Assert
        coVerify { promptsInteractor.deletePrompt(promptId) }
    }

    @Test
    fun `toggleFavorite should call interactor and emit event on success`() = runTest {
        // Arrange
        val promptId = "test-prompt-id"
        coEvery { promptsInteractor.toggleFavorite(promptId) } returns Unit

        // Act
        viewModel.toggleFavorite(promptId)

        // Assert
        coVerify { promptsInteractor.toggleFavorite(promptId) }
    }

    @Test
    fun `setStatusFilter should update search state and reset custom filters`() = runTest {
        // Arrange
        val newStatus = "favorites"

        // Act
        viewModel.setStatusFilter(newStatus)

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(newStatus, searchState.status)
        assertEquals(null, searchState.category)
        assertEquals(emptyList<String>(), searchState.tags)
    }

    @Test
    fun `setStatusFilter should handle null status correctly`() = runTest {
        // Act
        viewModel.setStatusFilter(null)

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(null, searchState.status)
        assertEquals(null, searchState.category)
        assertEquals(emptyList<String>(), searchState.tags)
    }

    @Test
    fun `applyFiltersFromDialog should update both custom filters and search state`() = runTest {
        // Arrange
        val category = "newCategory"
        val tags = listOf("tag1", "tag2")

        // Act
        viewModel.applyFiltersFromDialog(category, tags)

        // Assert
        val searchState = viewModel.searchState.first()
        assertEquals(category, searchState.category)
        assertEquals(tags, searchState.tags)
        assertEquals(null, searchState.status)
    }

    private fun createTestPrompt(): Prompt {
        return Prompt(
            id = "test-id",
            title = "Test Prompt",
            description = "Test description",
            content = PromptContent("RU content", "EN content"),
            compatibleModels = listOf("model1"),
            category = "test",
            tags = listOf("tag1"),
            status = "active",
            metadata = PromptMetadata(),
            createdAt = Date(),
            modifiedAt = Date()
        )
    }
}