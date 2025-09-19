import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.usecase.ImportJsonUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ImportJsonUseCaseTest {

    private val mockRepository = mockk<IPromptsRepository>()
    private val mockFileDataSource = mockk<FileDataSource>()
    private val useCase = ImportJsonUseCase(mockRepository, mockFileDataSource)

    @Test
    fun `invoke returns success with correct count when import succeeds`() = runTest {
        // Given
        val mockFiles: List<PlatformFile> = listOf(createMockFile("file1.json", validJsonContent()))

        coEvery { mockFileDataSource.getPromptFiles() } returns mockFiles
        coEvery { mockRepository.savePrompts(any()) } returns Unit

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `invoke returns success with zero count when no files found`() = runTest {
        // Given
        coEvery { mockFileDataSource.getPromptFiles() } returns emptyList()
        coEvery { mockRepository.savePrompts(any()) } returns Unit

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `invoke skips invalid json files and continues with valid ones`() = runTest {
        // Given
        val validFile = createMockFile("valid.json", validJsonContent())
        val invalidFile = createMockFile("invalid.json", "invalid json content")
        val mockFiles: List<PlatformFile> = listOf(validFile, invalidFile)

        coEvery { mockFileDataSource.getPromptFiles() } returns mockFiles
        coEvery { mockRepository.savePrompts(any()) } returns Unit

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()) // Only valid file processed
    }

    @Test
    fun `invoke returns failure when repository save fails`() = runTest {
        // Given
        val mockFiles: List<PlatformFile> = listOf(createMockFile("file1.json", validJsonContent()))

        coEvery { mockFileDataSource.getPromptFiles() } returns mockFiles
        coEvery { mockRepository.savePrompts(any()) } throws RuntimeException("Save failed")

        // When
        val result = useCase()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke handles multiple valid files correctly`() = runTest {
        // Given
        val file1 = createMockFile("file1.json", validJsonContent().replace("Test Prompt", "Test Prompt 1"))
        val file2 = createMockFile("file2.json", validJsonContent().replace("Test Prompt", "Test Prompt 2"))
        val mockFiles: List<PlatformFile> = listOf(file1, file2)

        coEvery { mockFileDataSource.getPromptFiles() } returns mockFiles
        coEvery { mockRepository.savePrompts(any()) } returns Unit

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }

    private fun createMockFile(name: String, content: String): PlatformFile {
        val mockFile = mockk<PlatformFile>()
        every { mockFile.name } returns name
        every { mockFile.isFile() } returns true
        coEvery { mockFile.readText() } returns content
        return mockFile
    }

    private fun validJsonContent(): String {
        return """
        {
            "id": "test-id",
            "title": "Test Prompt",
            "description": "Test description",
            "content": {
                "ru": "Русский контент",
                "en": "English content"
            },
            "category": "test",
            "status": "active",
            "isLocal": true,
            "isFavorite": false,
            "rating": {
                "score": 4.5,
                "votes": 10
            },
            "metadata": {
                "author": {
                    "name": "Test Author"
                },
                "source": "test",
                "notes": "Test notes"
            },
            "version": "1.0.0",
            "createdAt": "2023-01-01T12:00:00",
            "updatedAt": "2023-01-01T12:00:00"
        }
        """.trimIndent()
    }

    private fun createTestPrompt(): Prompt {
        return Prompt(
            id = "test-id",
            title = "Test Prompt",
            content = com.arny.aiprompts.domain.model.PromptContent(ru = "Русский контент", en = "English content"),
            description = "Test description",
            category = "test",
            status = "active",
            tags = emptyList(),
            isLocal = true,
            isFavorite = false,
            rating = 4.5f,
            ratingVotes = 10,
            compatibleModels = emptyList(),
            metadata = com.arny.aiprompts.data.model.PromptMetadata(
                author = com.arny.aiprompts.domain.model.Author(id = "Test Author", name = "Test Author"),
                source = "test",
                notes = "Test notes"
            ),
            version = "1.0.0",
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1672574400000), // 2023-01-01T12:00:00
            modifiedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1672574400000)
        )
    }
}