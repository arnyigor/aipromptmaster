import com.arny.aiprompts.data.parser.IForumPromptParser
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.usecase.ParseHtmlUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.io.File

class ParseHtmlUseCaseTest {

    private val mockParser = mockk<IForumPromptParser>()
    private val useCase = ParseHtmlUseCase(mockParser)

    @Test
    fun `invoke returns success when parser succeeds`() = runTest {
        // Given
        val tempFile = File.createTempFile("test", ".html").apply { writeText("<html>Test HTML</html>") }
        val testPrompts = listOf(createTestPromptData())
        coEvery { mockParser.parse("<html>Test HTML</html>") } returns testPrompts

        // When
        val result = useCase(tempFile)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testPrompts, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when parser fails`() = runTest {
        // Given
        val tempFile = File.createTempFile("test", ".html").apply { writeText("<html>Invalid HTML</html>") }
        val exception = RuntimeException("Parse error")
        coEvery { mockParser.parse("<html>Invalid HTML</html>") } throws exception

        // When
        val result = useCase(tempFile)

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