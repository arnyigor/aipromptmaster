import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.io.File

class SavePromptsAsFilesUseCaseTest {

    private val mockFileDataSource = mockk<FileDataSource>()
    private val useCase = SavePromptsAsFilesUseCase(mockFileDataSource)

    @Test
    fun `invoke returns success with files when save succeeds`() = runTest {
        // Given
        val testPrompts = listOf(createTestPromptData())
        val mockFile = mockk<File>()
        coEvery { mockFileDataSource.savePromptJson(any()) } returns mockFile

        // When
        val result = useCase(testPrompts)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun `invoke returns success with multiple files when multiple prompts saved`() = runTest {
        // Given
        val testPrompts = listOf(createTestPromptData(), createTestPromptData().copy(title = "Second Prompt"))
        val mockFile = mockk<File>()
        coEvery { mockFileDataSource.savePromptJson(any()) } returns mockFile

        // When
        val result = useCase(testPrompts)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `invoke returns failure when save fails`() = runTest {
        // Given
        val testPrompts = listOf(createTestPromptData())
        val exception = RuntimeException("Save failed")
        coEvery { mockFileDataSource.savePromptJson(any()) } throws exception

        // When
        val result = useCase(testPrompts)

        // Then
        assertTrue(result.isFailure)
    }

    private fun createTestPromptData(): PromptData {
        return PromptData(
            id = "test-id",
            sourceId = "source-123",
            title = "Test Prompt",
            description = "Test description",
            variants = listOf(PromptVariant(type = "prompt", content = "Test content")),
            author = Author(id = "author-1", name = "Test Author"),
            createdAt = 123456789L,
            updatedAt = 123456789L,
            tags = listOf("tag1"),
            category = "test"
        )
    }
}