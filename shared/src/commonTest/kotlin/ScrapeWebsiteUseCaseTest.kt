import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.File

class ScrapeWebsiteUseCaseTest {

    private val mockWebScraper = mockk<WebScraper>()
    private val useCase = ScrapeWebsiteUseCase(mockWebScraper)

    @Test
    fun `invoke emits in progress and success when scraping succeeds`() = runTest {
        // Given
        val testFiles = listOf(mockk<File>())
        coEvery { mockWebScraper.scrapeAndSave("https://example.com", listOf(1), any()) } answers {
            val progressCallback = thirdArg<(String) -> Unit>()
            progressCallback("Starting scrape")
            progressCallback("Page 1 processed")
            testFiles
        }

        // When
        val results = useCase("https://example.com", listOf(1)).toList()

        // Then
        assertEquals(4, results.size) // InProgress start, InProgress "Starting scrape", InProgress "Page 1 processed", Success
        assertTrue(results[0] is ScraperResult.InProgress)
        assertTrue(results[1] is ScraperResult.InProgress)
        assertTrue(results[2] is ScraperResult.InProgress)
        assertTrue(results[3] is ScraperResult.Success)
        val successResult = results[3] as ScraperResult.Success
        assertEquals(testFiles, successResult.files)
    }
}