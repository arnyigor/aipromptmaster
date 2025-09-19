import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class GetPromptsUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = GetPromptsUseCase(mockRepository)

    @Test
    fun `getPromptsFlow emits success result when repository succeeds`() = runTest {
        // Given
        val testPrompts = listOf(createTestPrompt())
        every { mockRepository.getAllPrompts() } returns flowOf(testPrompts)

        // When
        val results = useCase.getPromptsFlow().toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        assertEquals(testPrompts, result.getOrNull())
    }

    @Test
    fun `getPromptsFlow emits failure result when repository fails`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        every { mockRepository.getAllPrompts() } returns flowOf<List<Prompt>>().also {
            throw exception
        }

        // When
        val results = useCase.getPromptsFlow().toList()

        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
    }

    @Test
    fun `search returns success result when repository succeeds`() = runTest {
        // Given
        val testPrompts = listOf(createTestPrompt())
        coEvery {
            mockRepository.getPrompts(
                search = "test",
                category = null,
                status = null,
                tags = emptyList(),
                offset = 0,
                limit = 20
            )
        } returns testPrompts

        // When
        val result = useCase.search("test")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testPrompts, result.getOrNull())
    }

    @Test
    fun `search returns failure result when repository fails`() = runTest {
        // Given
        val exception = RuntimeException("Search failed")
        coEvery {
            mockRepository.getPrompts(
                search = "test",
                category = null,
                status = null,
                tags = emptyList(),
                offset = 0,
                limit = 20
            )
        } throws exception

        // When
        val result = useCase.search("test")

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `search with filters applies correct parameters`() = runTest {
        // Given
        val testPrompts = listOf(createTestPrompt())
        coEvery {
            mockRepository.getPrompts(
                search = "advanced search",
                category = "test",
                status = "active",
                tags = listOf("tag1", "tag2"),
                offset = 10,
                limit = 50
            )
        } returns testPrompts

        // When
        val result = useCase.search(
            query = "advanced search",
            category = "test",
            status = "active",
            tags = listOf("tag1", "tag2"),
            offset = 10,
            limit = 50
        )

        // Then
        assertTrue(result.isSuccess)
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