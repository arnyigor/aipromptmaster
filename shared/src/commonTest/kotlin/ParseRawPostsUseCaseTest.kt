import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.RawPostData
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File

class ParseRawPostsUseCaseTest {

    private val mockParser = mockk<IFileParser>()
    private val useCase = ParseRawPostsUseCase(mockParser)

    @Test
    fun `invoke returns success when parser succeeds`() = runTest {
        // Given
        val tempFile = File.createTempFile("test", ".txt").apply { writeText("Test HTML content") }
        val testRawPosts = listOf(createTestRawPostData())
        every { mockParser.parse("Test HTML content") } returns testRawPosts

        // When
        val result = useCase(tempFile)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testRawPosts, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when parser fails`() = runTest {
        // Given
        val tempFile = File.createTempFile("test", ".txt").apply { writeText("Invalid content") }
        val exception = RuntimeException("Parse error")
        every { mockParser.parse("Invalid content") } throws exception

        // When
        val result = useCase(tempFile)

        // Then
        assertTrue(result.isFailure)
    }

    private fun createTestRawPostData(): RawPostData {
        return RawPostData(
            postId = "post-123",
            author = Author(id = "author-1", name = "Test Author"),
            date = Instant.fromEpochMilliseconds(1234567890000L),
            fullHtmlContent = "Test content",
            isLikelyPrompt = true,
            postUrl = "https://example.com/post"
        )
    }
}