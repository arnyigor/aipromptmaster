import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DeletePromptUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = DeletePromptUseCase(mockRepository)

    @Test
    fun `invoke returns success result when repository succeeds`() = runTest {
        // Given
        coEvery {
            mockRepository.deletePrompt("test-id")
        } returns Unit

        // When
        val result = useCase("test-id")

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke returns failure result when repository fails`() = runTest {
        // Given
        val exception = RuntimeException("Delete failed")
        coEvery {
            mockRepository.deletePrompt("test-id")
        } throws exception

        // When
        val result = useCase("test-id")

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke handles different prompt ids correctly`() = runTest {
        // Given
        val testIds = listOf("id1", "id2", "special-id")
        coEvery {
            mockRepository.deletePrompt(any())
        } returns Unit

        // When & Then
        testIds.forEach { id ->
            val result = useCase(id)
            assertTrue(result.isSuccess, "Failed for id: $id")
        }
    }

    @Test
    fun `invoke handles non-existent prompt id gracefully`() = runTest {
        // Given
        val nonExistentId = "non-existent-id"
        val exception = NoSuchElementException("Prompt not found")
        coEvery {
            mockRepository.deletePrompt(nonExistentId)
        } throws exception

        // When
        val result = useCase(nonExistentId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke handles empty prompt id`() = runTest {
        // Given
        val emptyId = ""
        coEvery {
            mockRepository.deletePrompt(emptyId)
        } returns Unit

        // When
        val result = useCase(emptyId)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke handles null or blank prompt id`() = runTest {
        // Given
        val blankId = "   "
        coEvery {
            mockRepository.deletePrompt(blankId)
        } returns Unit

        // When
        val result = useCase(blankId)

        // Then
        assertTrue(result.isSuccess)
    }
}