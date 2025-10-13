package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.PromptMetadata
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class PromptsInteractorImplTest {

    private lateinit var repository: IPromptsRepository
    private lateinit var synchronizer: IPromptSynchronizer
    private lateinit var interactor: PromptsInteractorImpl

    @Before
    fun setup() {
        repository = mockk()
        synchronizer = mockk()
        interactor = PromptsInteractorImpl(repository, synchronizer)
    }

    @Test
    fun `getPrompts should delegate to repository with correct parameters`() = runTest {
        // Arrange
        val query = "test query"
        val category = "test category"
        val status = "active"
        val tags = listOf("tag1", "tag2")
        val offset = 10
        val limit = 20
        val expectedPrompts = listOf(createTestPrompt())

        coEvery {
            repository.getPrompts(
                search = query,
                category = category,
                status = status,
                tags = tags,
                offset = offset,
                limit = limit
            )
        } returns expectedPrompts

        // Act
        val result = interactor.getPrompts(query, category, status, tags, offset, limit)

        // Assert
        assertEquals(expectedPrompts, result)
        coVerify {
            repository.getPrompts(
                search = query,
                category = category,
                status = status,
                tags = tags,
                offset = offset,
                limit = limit
            )
        }
    }

    @Test
    fun `getPrompts should use default parameters when not provided`() = runTest {
        // Arrange
        val expectedPrompts = emptyList<Prompt>()
        coEvery {
            repository.getPrompts("", null, null, emptyList(), 0, 20)
        } returns expectedPrompts

        // Act
        val result = interactor.getPrompts()

        // Assert
        assertEquals(expectedPrompts, result)
        coVerify {
            repository.getPrompts("", null, null, emptyList(), 0, 20)
        }
    }

    @Test
    fun `getPromptById should delegate to repository`() = runTest {
        // Arrange
        val promptId = "test-conversationId"
        val expectedPrompt = createTestPrompt()
        coEvery { repository.getPromptById(promptId) } returns expectedPrompt

        // Act
        val result = interactor.getPromptById(promptId)

        // Assert
        assertEquals(expectedPrompt, result)
        coVerify { repository.getPromptById(promptId) }
    }

    @Test
    fun `getPromptById should return null when prompt not found`() = runTest {
        // Arrange
        val promptId = "non-existent-conversationId"
        coEvery { repository.getPromptById(promptId) } returns null

        // Act
        val result = interactor.getPromptById(promptId)

        // Assert
        assertNull(result)
        coVerify { repository.getPromptById(promptId) }
    }

    @Test
    fun `savePrompt should delegate to repository and return id`() = runTest {
        // Arrange
        val prompt = createTestPrompt()
        val expectedId = 123L
        coEvery { repository.insertPrompt(prompt) } returns expectedId

        // Act
        val result = interactor.savePrompt(prompt)

        // Assert
        assertEquals(expectedId, result)
        coVerify { repository.insertPrompt(prompt) }
    }

    @Test
    fun `updatePrompt should delegate to repository`() = runTest {
        // Arrange
        val prompt = createTestPrompt()
        coEvery { repository.updatePrompt(prompt) } returns Unit

        // Act
        interactor.updatePrompt(prompt)

        // Assert
        coVerify { repository.updatePrompt(prompt) }
    }

    @Test
    fun `deletePrompt should delegate to repository`() = runTest {
        // Arrange
        val promptId = "test-conversationId"
        coEvery { repository.deletePrompt(promptId) } returns Unit

        // Act
        interactor.deletePrompt(promptId)

        // Assert
        coVerify { repository.deletePrompt(promptId) }
    }

    @Test
    fun `synchronize should delegate to synchronizer and return success result`() = runTest {
        // Arrange
        val expectedResult = SyncResult.Success(emptyList())
        coEvery { synchronizer.synchronize() } returns expectedResult

        // Act
        val result = interactor.synchronize()

        // Assert
        assertEquals(expectedResult, result)
        coVerify { synchronizer.synchronize() }
    }

    @Test
    fun `synchronize should handle error result from synchronizer`() = runTest {
        // Arrange
        val errorMessage = StringHolder.Text("Sync failed")
        val expectedResult = SyncResult.Error(errorMessage)
        coEvery { synchronizer.synchronize() } returns expectedResult

        // Act
        val result = interactor.synchronize()

        // Assert
        assertEquals(expectedResult, result)
        coVerify { synchronizer.synchronize() }
    }

    @Test
    fun `getLastSyncTime should delegate to synchronizer`() = runTest {
        // Arrange
        val expectedTime = 123456789L
        coEvery { synchronizer.getLastSyncTime() } returns expectedTime

        // Act
        val result = interactor.getLastSyncTime()

        // Assert
        assertEquals(expectedTime, result)
        coVerify { synchronizer.getLastSyncTime() }
    }

    @Test
    fun `toggleFavorite should update prompt favorite status when prompt exists`() = runTest {
        // Arrange
        val promptId = "test-conversationId"
        val existingPrompt = createTestPrompt(id = promptId, isFavorite = false)
        val updatedPrompt = existingPrompt.copy(isFavorite = true)

        coEvery { repository.getPromptById(promptId) } returns existingPrompt
        coEvery { repository.updatePrompt(updatedPrompt) } returns Unit

        // Act
        interactor.toggleFavorite(promptId)

        // Assert
        coVerify {
            repository.getPromptById(promptId)
            repository.updatePrompt(updatedPrompt)
        }
    }

    @Test
    fun `toggleFavorite should not update when prompt does not exist`() = runTest {
        // Arrange
        val promptId = "non-existent-conversationId"
        coEvery { repository.getPromptById(promptId) } returns null

        // Act
        interactor.toggleFavorite(promptId)

        // Assert
        coVerify { repository.getPromptById(promptId) }
        coVerify(exactly = 0) { repository.updatePrompt(any()) }
    }

    @Test
    fun `addCategory should add new category to existing categories`() = runTest {
        // Arrange
        val existingCategories = listOf("category1", "category2")
        val newCategory = "newCategory"
        val existingSortData = PromptsSortData(categories = existingCategories, tags = emptyList())
        val expectedSortData = PromptsSortData(categories = existingCategories + newCategory, tags = emptyList())

        coEvery { repository.getCacheSortData() } returns existingSortData
        every { repository.cacheSortData(expectedSortData) } returns Unit

        // Act
        interactor.addCategory(newCategory)

        // Assert
        verify { repository.cacheSortData(expectedSortData) }
    }

    @Test
    fun `addCategory should not add duplicate category`() = runTest {
        // Arrange
        val existingCategories = listOf("category1", "existingCategory")
        val existingSortData = PromptsSortData(categories = existingCategories, tags = emptyList())

        coEvery { repository.getCacheSortData() } returns existingSortData

        // Act
        interactor.addCategory("existingCategory")

        // Assert
        verify(exactly = 0) { repository.cacheSortData(any()) }
    }

    @Test
    fun `getPromptsSortData should return cached data when available`() = runTest {
        // Arrange
        val cachedSortData = PromptsSortData(categories = listOf("cat1"), tags = listOf("tag1"))
        coEvery { repository.getCacheSortData() } returns cachedSortData

        // Act
        val result = interactor.getPromptsSortData()

        // Assert
        assertEquals(cachedSortData, result)
        coVerify { repository.getCacheSortData() }
        coVerify(exactly = 0) { repository.getUniqueCategories() }
        coVerify(exactly = 0) { repository.getUniqueTags() }
    }

    @Test
    fun `getPromptsSortData should load and cache data when cache is empty`() = runTest {
        // Arrange
        val categories = listOf("cat1", "cat2")
        val tags = listOf("tag1", "tag2")
        val expectedSortData = PromptsSortData(categories = categories, tags = tags)

        coEvery { repository.getCacheSortData() } returns null
        coEvery { repository.getUniqueCategories() } returns categories
        coEvery { repository.getUniqueTags() } returns tags
        every { repository.cacheSortData(expectedSortData) } returns Unit

        // Act
        val result = interactor.getPromptsSortData()

        // Assert
        assertEquals(expectedSortData, result)
        verify { repository.cacheSortData(expectedSortData) }
    }

    private fun createTestPrompt(
        id: String = "test-conversationId",
        title: String = "Test Prompt",
        isFavorite: Boolean = false
    ): Prompt {
        return Prompt(
            id = id,
            title = title,
            description = "Test description",
            content = PromptContent("RU content", "EN content"),
            compatibleModels = listOf("model1"),
            category = "test",
            tags = listOf("tag1"),
            isFavorite = isFavorite,
            status = "active",
            metadata = PromptMetadata(),
            createdAt = Date(),
            modifiedAt = Date()
        )
    }
}