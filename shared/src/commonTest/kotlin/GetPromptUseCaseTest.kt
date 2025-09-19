import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class GetPromptUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = GetPromptUseCase(mockRepository)

    @Test
    fun `getPromptFlow emits success result when repository succeeds`() = runTest {
        // Given
        val testPrompt = createTestPrompt()
        coEvery { mockRepository.getPromptById("test-id") } returns testPrompt

        // When
        val results = useCase.getPromptFlow("test-id").toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        assertEquals(testPrompt, result.getOrNull())
    }

    @Test
    fun `getPromptFlow emits failure result when repository fails`() = runTest {
        // Given
        val exception = RuntimeException("Prompt not found")
        coEvery { mockRepository.getPromptById("test-id") } throws exception

        // When
        val results = useCase.getPromptFlow("test-id").toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
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