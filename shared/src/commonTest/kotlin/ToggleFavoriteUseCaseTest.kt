import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
        coVerify { mockRepository.toggleFavoriteStatus(promptId) }
    }

    @Test
    fun `invoke throws when repository throws exception`() = runTest {
        // Given
        val promptId = "test-prompt-id"
        val exception = RuntimeException("Toggle failed")
        coEvery { mockRepository.toggleFavoriteStatus(promptId) } throws exception

        // When & Then
        assertFailsWith<RuntimeException> {
            useCase(promptId)
        }

        // Verify the call was made
        coVerify { mockRepository.toggleFavoriteStatus(promptId) }
    }
}