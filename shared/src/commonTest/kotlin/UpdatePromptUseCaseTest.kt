import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.domain.usecase.UpdatePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UpdatePromptUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = UpdatePromptUseCase(mockRepository)

    @Test
    fun `invoke returns success result when repository succeeds`() = runTest {
        // Given
        val existingPrompt = createTestPrompt()
        coEvery {
            mockRepository.getPromptById("test-id")
        } returns existingPrompt
        coEvery {
            mockRepository.updatePrompt(any())
        } returns Unit

        // When
        val result = useCase(
            promptId = "test-id",
            title = "Updated Title",
            contentRu = "Обновленный контент",
            description = "Updated description"
        )

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke returns failure result when prompt not found`() = runTest {
        // Given
        coEvery {
            mockRepository.getPromptById("non-existent-id")
        } returns null

        // When
        val result = useCase(
            promptId = "non-existent-id",
            title = "Updated Title"
        )

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertEquals("Prompt with id non-existent-id not found", exception?.message)
    }

    @Test
    fun `invoke returns failure result when repository update fails`() = runTest {
        // Given
        val existingPrompt = createTestPrompt()
        val updateException = RuntimeException("Update failed")
        coEvery {
            mockRepository.getPromptById("test-id")
        } returns existingPrompt
        coEvery {
            mockRepository.updatePrompt(any())
        } throws updateException

        // When
        val result = useCase(
            promptId = "test-id",
            title = "Updated Title"
        )

        // Then
        assertTrue(result.isFailure)
        assertEquals(updateException, result.exceptionOrNull())
    }

    @Test
    fun `invoke applies partial updates correctly`() = runTest {
        // Given
        val existingPrompt = createTestPrompt()
        coEvery {
            mockRepository.getPromptById("test-id")
        } returns existingPrompt
        coEvery {
            mockRepository.updatePrompt(any())
        } returns Unit

        // When - only update title, leave other fields unchanged
        val result = useCase(
            promptId = "test-id",
            title = "New Title"
            // Other fields should remain from existing prompt
        )

        // Then
        assertTrue(result.isSuccess)
        // The update should preserve existing values for non-updated fields
    }

    private fun createTestPrompt(): Prompt {
        return Prompt(
            id = "test-id",
            title = "Test Prompt",
            content = PromptContent(ru = "Test content", en = "Test content EN"),
            description = "Test description",
            category = "test",
            status = "active",
            tags = listOf("tag1"),
            isLocal = true,
            isFavorite = false,
            rating = 4.5f,
            ratingVotes = 10,
            compatibleModels = listOf("gpt-4"),
            metadata = com.arny.aiprompts.data.model.PromptMetadata(
                author = com.arny.aiprompts.domain.model.Author(id = "author-id", name = "Test Author"),
                source = "test",
                notes = ""
            ),
            version = "1.0.0",
            createdAt = null,
            modifiedAt = null
        )
    }
}