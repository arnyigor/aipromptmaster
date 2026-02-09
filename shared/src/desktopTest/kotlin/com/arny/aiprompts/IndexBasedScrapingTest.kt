package com.arny.aiprompts

import com.arny.aiprompts.domain.index.IndexCacheManager
import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.SmartScraper
import com.arny.aiprompts.domain.index.model.IndexParseResult
import com.arny.aiprompts.domain.index.model.ParsedIndex
import com.arny.aiprompts.domain.index.model.PostLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.jsoup.Jsoup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Integration test for Index-Based Scraping.
 * Tests the complete flow: download index page -> parse links -> resolve locations -> download posts.
 */
@OptIn(ExperimentalTime::class, DelicateCoroutinesApi::class)
class IndexBasedScrapingTest {

    companion object {
        private const val TEST_TOPIC_URL = "https://4pda.to/forum/index.php?showtopic=1109539"
        private const val TEST_TOPIC_ID = "1109539"
        private const val CACHE_DIR = "test_index_cache"
    }

    private lateinit var indexParser: IndexParser
    private lateinit var cacheManager: IndexCacheManager

    @BeforeEach
    fun setup() {
        indexParser = IndexParser()
        cacheManager = IndexCacheManager()
    }

    /**
     * Test 1: Download and parse index page (first page of topic).
     * Verifies that we can extract links from spoilers.
     */
    @Test
    fun `step 1 - download and parse index page`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("STEP 1: Downloading and parsing index page")
        println("=".repeat(80))

        // Download first page using Selenium
        val htmlContent = downloadPageWithSelenium(TEST_TOPIC_URL)
        assertNotNull(htmlContent, "Failed to download index page")
        println("✓ Downloaded ${htmlContent.length} characters")

        // Save raw HTML for inspection
        saveRawHtml("index_page_raw.html", htmlContent)

        // Parse index
        when (val result = indexParser.parseIndex(htmlContent, TEST_TOPIC_URL)) {
            is IndexParseResult.Success -> {
                printIndexStats(result.index)
                
                // Save parsed index
                cacheManager.saveCache(result.index)
                println("✓ Index saved to cache")
            }
            is IndexParseResult.Error -> {
                throw AssertionError("Failed to parse index: ${result.message}")
            }
            else -> throw AssertionError("Unexpected result type")
        }
    }

    /**
     * Test 2: Use cached index to resolve page locations.
     * Uses redirect resolution (Variant A).
     */
    @Test
    fun `step 2 - resolve page locations for first 3 links`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("STEP 2: Resolving page locations (first 3 links only)")
        println("=".repeat(80))

        // Load cached index
        val index = cacheManager.loadCache(TEST_TOPIC_ID)
        assertNotNull(index, "No cached index found. Run step 1 first.")
        
        println("Loaded index with ${index.links.size} links")
        
        // Take only first 3 links for testing
        val testLinks = index.links.take(3)
        println("Testing with first ${testLinks.size} links:")
        testLinks.forEach { 
            println("  - Post ${it.postId}: ${it.spoilerTitle?.take(50) ?: "No title"}")
        }

        // Create test index with only 3 links
        val testIndex = index.copy(links = testLinks)

        // Resolve locations
        val locations = resolveLocationsManually(testIndex)
        
        println("\nResolved locations:")
        locations.forEach { loc ->
            println("  Post ${loc.postId} -> st=${loc.pageOffset}")
        }

        // Update index with locations
        val updatedIndex = indexParser.updateWithPageLocations(testIndex, locations)
        cacheManager.saveCache(updatedIndex)
        println("✓ Updated index saved with page locations")
    }

    /**
     * Test 3: Download specific pages and extract posts.
     */
    @Test
    fun `step 3 - download pages and extract posts`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("STEP 3: Downloading pages and extracting posts")
        println("=".repeat(80))

        // Load index with resolved locations
        val index = cacheManager.loadCache(TEST_TOPIC_ID)
        assertNotNull(index, "No cached index found")
        
        val locations = index.links.mapNotNull { link ->
            link.pageOffset?.let { offset ->
                PostLocation(link.postId, offset, "")
            }
        }

        println("Found ${locations.size} locations to process")

        // Group by page
        val byPage = locations.groupBy { it.pageOffset }
        println("Need to download ${byPage.size} unique pages")

        // Download each page and extract posts
        byPage.forEach { (pageOffset, pageLocations) ->
            println("\n--- Processing page st=$pageOffset ---")
            
            val pageUrl = buildPageUrl(pageOffset)
            val html = downloadPageWithSelenium(pageUrl)
            
            if (html != null) {
                // Save page for inspection
                saveRawHtml("page_st_${pageOffset}.html", html)
                
                // Extract posts
                extractAndPrintPosts(html, pageLocations.map { it.postId })
            } else {
                println("✗ Failed to download page st=$pageOffset")
            }
        }
    }

    /**
     * Test 4: Full pipeline test (can be run independently).
     */
    @Test
    fun `full pipeline test with limit`() = runBlocking {
        println("\n" + "=".repeat(80))
        println("FULL PIPELINE TEST (limited to 2 links)")
        println("=".repeat(80))

        // 1. Download index
        println("\n[1/4] Downloading index page...")
        val htmlContent = downloadPageWithSelenium(TEST_TOPIC_URL)
        assertNotNull(htmlContent)

        // 2. Parse index
        println("[2/4] Parsing index...")
        val result = indexParser.parseIndex(htmlContent, TEST_TOPIC_URL)
        assertTrue(result is IndexParseResult.Success, "Failed to parse index")
        
        val index = (result as IndexParseResult.Success).index
        println("✓ Found ${index.links.size} links in index")

        // Limit to 2 links for quick test
        val limitedIndex = index.copy(links = index.links.take(2))
        println("Limited to ${limitedIndex.links.size} links for testing")

        // 3. Resolve locations
        println("\n[3/4] Resolving page locations...")
        val locations = resolveLocationsManually(limitedIndex)
        println("✓ Resolved ${locations.size} locations")

        // 4. Download and extract
        println("\n[4/4] Downloading posts...")
        locations.forEach { location ->
            val pageUrl = buildPageUrl(location.pageOffset)
            println("Downloading page: $pageUrl")
            
            val html = downloadPageWithSelenium(pageUrl)
            if (html != null) {
                val post = extractPost(html, location.postId)
                if (post != null) {
                    println("✓ Successfully extracted post ${location.postId}")
                    println("  Author: ${post.authorName}")
                    println("  Content length: ${post.textContent.length} chars")
                    println("  Preview: ${post.textContent.take(100)}...")
                } else {
                    println("✗ Post ${location.postId} not found on page")
                }
            }
        }

        println("\n" + "=".repeat(80))
        println("TEST COMPLETED")
        println("=".repeat(80))
    }

    // === Helper Methods ===

    private fun downloadPageWithSelenium(url: String): String? {
        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--no-sandbox")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }

        val driver = ChromeDriver(options)
        return try {
            driver.get(url)
            Thread.sleep(3000) // Wait for page load
            driver.pageSource
        } catch (e: Exception) {
            println("Error downloading page: ${e.message}")
            null
        } finally {
            driver.quit()
        }
    }

    private fun resolveLocationsManually(index: ParsedIndex): List<PostLocation> {
        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
        }

        val driver = ChromeDriver(options)
        val locations = mutableListOf<PostLocation>()

        try {
            index.links.forEach { link ->
                val findpostUrl = "https://4pda.to/forum/index.php?showtopic=${index.topicId}&view=findpost&p=${link.postId}"
                println("  Resolving: $findpostUrl")
                
                try {
                    driver.get(findpostUrl)
                    Thread.sleep(2000)
                    
                    val finalUrl = driver.currentUrl
                    val pageOffset = extractPageOffset(finalUrl) ?: 0
                    
                    locations.add(PostLocation(link.postId, pageOffset, finalUrl))
                    println("    -> st=$pageOffset")
                    
                    Thread.sleep(1000) // Delay between requests
                } catch (e: Exception) {
                    println("    -> Error: ${e.message}")
                }
            }
        } finally {
            driver.quit()
        }

        return locations
    }

    private fun extractPageOffset(url: String): Int? {
        val regex = Regex("""[?&]st=(\d+)""")
        val match = regex.find(url)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun buildPageUrl(pageOffset: Int): String {
        return if (pageOffset == 0) {
            TEST_TOPIC_URL
        } else {
            "$TEST_TOPIC_URL&st=$pageOffset"
        }
    }

    private fun extractAndPrintPosts(html: String, postIds: List<String>) {
        val document = Jsoup.parse(html)
        
        postIds.forEach { postId ->
            val postElement = document.selectFirst("div.post[data-post='$postId']")
            
            if (postElement != null) {
                val authorName = postElement.selectFirst("a[href*='showuser']")?.text() ?: "Unknown"
                val contentElement = postElement.selectFirst("div.postcolor")
                val textContent = contentElement?.text() ?: ""
                
                println("\n  Post $postId:")
                println("    Author: $authorName")
                println("    Content length: ${textContent.length} chars")
                println("    Preview: ${textContent.take(150)}...")
                
                // Check for spoilers (prompts are usually in spoilers)
                val spoilers = postElement.select("div.post-block.spoil")
                println("    Spoilers found: ${spoilers.size}")
                spoilers.forEachIndexed { idx, spoiler ->
                    val title = spoiler.selectFirst(".block-title")?.text()?.take(40)
                    println("      Spoiler ${idx + 1}: ${title ?: "No title"}")
                }
            } else {
                println("  ✗ Post $postId not found")
            }
        }
    }

    private fun extractPost(html: String, postId: String): PostInfo? {
        val document = Jsoup.parse(html)
        val postElement = document.selectFirst("div.post[data-post='$postId']") ?: return null
        
        val authorName = postElement.selectFirst("a[href*='showuser']")?.text() ?: "Unknown"
        val contentElement = postElement.selectFirst("div.postcolor")
        val htmlContent = contentElement?.html() ?: ""
        val textContent = contentElement?.text() ?: ""
        
        return PostInfo(postId, authorName, htmlContent, textContent)
    }

    private fun saveRawHtml(filename: String, content: String) {
        val testDir = File(System.getProperty("user.home"), ".aiprompts/test_output")
        testDir.mkdirs()
        File(testDir, filename).writeText(content)
        println("Saved: ${testDir.absolutePath}/$filename")
    }

    private fun printIndexStats(index: ParsedIndex) {
        println("\n--- INDEX STATISTICS ---")
        println("Topic ID: ${index.topicId}")
        println("Total unique links: ${index.links.size}")
        
        // Group by category
        val byCategory = index.links.groupingBy { it.category ?: "Unknown" }.eachCount()
        println("\nBy category:")
        byCategory.forEach { (cat, count) ->
            println("  $cat: $count")
        }

        // Show first 5 links
        println("\nFirst 5 links:")
        index.links.take(5).forEach { link ->
            println("  Post ${link.postId}: ${link.spoilerTitle?.take(50) ?: "No title"}")
        }
    }

    data class PostInfo(
        val postId: String,
        val authorName: String,
        val htmlContent: String,
        val textContent: String
    )
}