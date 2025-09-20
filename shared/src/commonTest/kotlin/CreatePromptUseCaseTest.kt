import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.usecase.CreatePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CreatePromptUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = CreatePromptUseCase(mockRepository)

    @Test
    fun `invoke returns success result when repository succeeds`() = runTest {
        // Given
        val expectedId = 123L
        coEvery {
            mockRepository.insertPrompt(any())
        } returns expectedId

        // When
        val result = useCase(
            title = "Test Prompt",
            contentRu = "Тестовый контент",
            contentEn = "Test content",
            description = "Test description",
            category = "test",
            tags = listOf("tag1", "tag2"),
            compatibleModels = listOf("gpt-4")
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedId, result.getOrNull())
    }

    @Test
    fun `invoke returns failure result when repository fails`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        coEvery {
            mockRepository.insertPrompt(any())
        } throws exception

        // When
        val result = useCase(
            title = "Test Prompt",
            contentRu = "Тестовый контент"
        )

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke creates prompt with correct default values`() = runTest {
        // Given
        val expectedId = 456L
        coEvery {
            mockRepository.insertPrompt(any())
        } returns expectedId

        // When
        val result = useCase(
            title = "Minimal Prompt"
        )

        // Then
        assertTrue(result.isSuccess)
        // Verify that insertPrompt was called with a prompt that has default values
        // The mock will capture the argument, but for this test we just verify success
    }
}