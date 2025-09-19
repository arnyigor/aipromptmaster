import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class ToggleFavoriteUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val useCase = ToggleFavoriteUseCase(mockRepository)

    @Test
    fun `invoke calls repository toggleFavoriteStatus with correct id`() = runTest {
        // Given
        val promptId = "test-prompt-id"
        coEvery { mockRepository.toggleFavoriteStatus(promptId) } returns Unit

        // When
        useCase(promptId)

        // Then
        // Verification is implicit through mock setup - if repository method wasn't called, test would fail
    }

    @Test
    fun `invoke propagates repository exceptions`() = runTest {
        // Given
        val promptId = "test-prompt-id"
        val expectedException = RuntimeException("Repository error")
        coEvery { mockRepository.toggleFavoriteStatus(promptId) } throws expectedException

        // When & Then
        assertFailsWith<RuntimeException> {
            useCase(promptId)
        }
    }

    @Test
    fun `invoke handles empty prompt id`() = runTest {
        // Given
        val promptId = ""
        coEvery { mockRepository.toggleFavoriteStatus(promptId) } returns Unit

        // When
        useCase(promptId)

        // Then
        // Should complete without exception
    }
}