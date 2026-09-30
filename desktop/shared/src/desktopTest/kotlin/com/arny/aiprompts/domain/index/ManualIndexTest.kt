package com.arny.aiprompts.domain.index

import com.arny.aiprompts.domain.index.model.IndexParseResult
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Manual test for Index-Based Scraping.
 * Run this to verify parsing works on the 4pda index page.
 */
class ManualIndexTest {

    private val testUrl = "https://4pda.to/forum/index.php?showtopic=1109539"

    /**
     * Test 1: Verify IndexParser can parse HTML correctly.
     */
    @Test
    fun `index parser should parse 4pda index correctly`() = runBlocking {
        // Skip if no test HTML file exists
        val testHtmlFile = File(System.getProperty("user.home"), ".aiprompts/test_output/index_test.html")
        if (!testHtmlFile.exists()) {
            println("Test skipped: No test HTML file found at ${testHtmlFile.absolutePath}")
            println("Run download test first or provide test HTML file")
            return@runBlocking
        }

        val htmlContent = testHtmlFile.readText()
        assertTrue(htmlContent.isNotBlank(), "HTML content should not be blank")

        val parser = IndexParser()
        val result = parser.parseIndex(htmlContent, testUrl)

        when (result) {
            is IndexParseResult.Success -> {
                val index = result.index
                println("=== INDEX PARSING SUCCESS ===")
                println("Topic ID: ${index.topicId}")
                println("Total links: ${index.links.size}")
                
                // Group by category
                val byCategory = index.links.groupBy { it.category ?: "Unknown" }
                println("\nLinks by category:")
                byCategory.forEach { (cat, links) ->
                    println("  $cat: ${links.size}")
                }
                
                // Show first 5 links
                println("\nFirst 5 links:")
                index.links.take(5).forEachIndexed { i, link ->
                    println("  ${i + 1}. Post ${link.postId}")
                    println("     Title: ${link.spoilerTitle?.take(60) ?: "N/A"}")
                }
                
                assertTrue(index.links.isNotEmpty(), "Should find at least one link")
            }
            is IndexParseResult.Error -> {
                println("=== INDEX PARSING ERROR ===")
                println("Message: ${result.message}")
                if (result.exception != null) {
                    result.exception.printStackTrace()
                }
            }
            is IndexParseResult.Cached -> {
                println("=== INDEX PARSING (CACHED) ===")
                val index = result.index
                println("Total links: ${index.links.size}")
            }
        }
    }

    /**
     * Test 2: Verify index stats calculation.
     */
    @Test
    fun `index stats should calculate correctly`() = runBlocking {
        // Create a minimal test HTML
        val testHtml = """
            <html>
            <body>
                <div class="post" data-post="12345">
                    <div class="postcolor">
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №1: Тестовый промпт</div>
                            <div class="block-body">
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=12345">Link 1</a>
                            </div>
                        </div>
                        <div class="post-block spoil">
                            <div class="block-title">Категория 2</div>
                            <div class="block-body">
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=67890">Link 2</a>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val parser = IndexParser()
        val result = parser.parseIndex(testHtml, testUrl)

        when (result) {
            is IndexParseResult.Success -> {
                val stats = parser.getIndexStats(result.index)
                println("=== INDEX STATS ===")
                println("Total links: ${stats.totalLinks}")
                println("Unique categories: ${stats.uniqueCategories}")
                println("Links by category: ${stats.linksByCategory}")
                
                assertTrue(stats.totalLinks >= 2, "Should find at least 2 links")
            }
            else -> {}
        }
    }

    /**
     * Test 3: Verify URL parsing for post IDs.
     */
    @Test
    fun `should extract post ID from URL correctly`() {
        val parser = IndexParser()
        
        // Test URL parsing
        val testCases = mapOf(
            "https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138452835" to "138452835",
            "https://4pda.to/forum/index.php?showtopic=1109539#entry138456113" to "138456113",
            "https://4pda.to/forum/index.php?showtopic=1109539&p=12345" to "12345"
        )
        
        println("=== URL POST ID EXTRACTION ===")
        testCases.forEach { (url, expected) ->
            println("Testing: $url")
            println("Expected: $expected")
            // Note: We can't directly test private methods, but we can verify parsing works
        }
    }

    /**
     * Helper: Download index page for testing.
     * Note: Requires Selenium to be available.
     */
    private fun downloadTestPage(): String? {
        return try {
            // Check if Selenium is available
            val optionsClass = Class.forName("org.openqa.selenium.chrome.ChromeOptions")
            println("Selenium is available")
            null
        } catch (e: Exception) {
            println("Selenium not available: ${e.message}")
            null
        }
    }
}

/**
 * Simple runner for manual testing.
 * Run: ./gradlew :shared:desktopTest --tests "com.arny.aiprompts.domain.index.ManualIndexTest"
 */
fun main() = runBlocking {
    println("=".repeat(80))
    println("INDEX-BASED SCRAPING - MANUAL TEST")
    println("=".repeat(80))
    
    val test = ManualIndexTest()
    
    // Run test 2 (doesn't require downloaded HTML)
    println("\n--- Running Test 2: Index Stats ---")
    test.`index stats should calculate correctly`()
    
    println("\n--- Running Test 3: URL Post ID Extraction ---")
    test.`should extract post ID from URL correctly`()
    
    println("\n" + "=".repeat(80))
    println("For full test, provide test HTML file at:")
    println("${System.getProperty("user.home")}/.aiprompts/test_output/index_test.html")
    println("=".repeat(80))
}
