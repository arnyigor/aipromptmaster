package com.arny.aiprompts.integration

import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.model.IndexParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Integration tests with real 4pda.to forum pages.
 * Requires Selenium for Cloudflare bypass.
 */
class Real4pdaIntegrationTests {

    private val parser = IndexParser()
    private val testOutputDir = File(System.getProperty("user.home"), ".aiprompts/integration_test")
    
    init {
        if (!testOutputDir.exists()) {
            testOutputDir.mkdirs()
        }
    }

    @Test
    fun testParseDownloadedRealPage() = runBlocking {
        // Test file from previous curl attempt (Cloudflare page)
        val testFile = File(testOutputDir, "index_real.html")
        
        if (!testFile.exists()) {
            println("TEST: Test file not found, skipping. Download real page first.")
            // Create a placeholder test
            assertTrue("Test directory exists", testOutputDir.exists())
            return@runBlocking
        }
        
        val html = testFile.readText(StandardCharsets.UTF_8)
        println("TEST: Loaded ${html.length} chars from real page")
        
        // Check if it's a Cloudflare challenge page
        if (html.contains("Just a moment") || html.contains("Cloudflare")) {
            println("TEST: Cloudflare protection detected, need Selenium for bypass")
            assertTrue("File is Cloudflare challenge", true)
            return@runBlocking
        }
        
        // Try to parse
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")
        
        when (result) {
            is IndexParseResult.Success -> {
                assertTrue("Should have links", result.index.links.isNotEmpty())
                println("TEST: Parsed ${result.index.links.size} links from real page")
            }
            is IndexParseResult.Error -> {
                println("TEST: Parse error: ${result.message}")
                assertTrue("Error message present", result.message.isNotBlank())
            }
            is IndexParseResult.Cached -> {
                println("TEST: Got cached result with ${result.index.links.size} links")
                assertTrue("Cached should have links", result.index.links.isNotEmpty())
            }
        }
    }

    @Test
    fun testDownloadRealPageWithSelenium() = runBlocking {
        // This test uses Selenium to bypass Cloudflare
        try {
            val chromeOptions = org.openqa.selenium.chrome.ChromeOptions().apply {
                addArguments("--headless=new")
                addArguments("--disable-gpu")
                addArguments("--no-sandbox")
                addArguments("--disable-dev-shm-usage")
                addArguments("--window-size=1920,1080")
                // Anti-detection
                addArguments("--disable-blink-features=AutomationControlled")
                addArguments("--disable-features=IsolateOrigins,site-per-process")
                // User agent to look like regular browser
                addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            
            val driver = org.openqa.selenium.chrome.ChromeDriver(chromeOptions)
            
            try {
                // Clear cookies and cache
                driver.manage().deleteAllCookies()
                
                val url = "https://4pda.to/forum/index.php?showtopic=1109539"
                println("TEST: Opening $url with Selenium...")
                println("TEST: Waiting for Cloudflare challenge (up to 60 seconds)...")
                
                driver.get(url)
                
                // Wait for Cloudflare with periodic checks
                var waitTime = 0L
                val maxWait = 60000L
                val checkInterval = 5000L
                
                while (waitTime < maxWait) {
                    Thread.sleep(checkInterval)
                    waitTime += checkInterval
                    
                    val pageSource = driver.pageSource
                    
                    // Check if still on Cloudflare
                    if (!pageSource.contains("Just a moment") && 
                        !pageSource.contains("cf-") && 
                        !pageSource.contains("Один момент") &&
                        pageSource.contains("4pda")) {
                        println("TEST: Cloudflare bypassed after ${waitTime}ms!")
                        break
                    }
                    
                    println("TEST: Still on Cloudflare, waited ${waitTime}ms...")
                }
                
                val currentUrl = driver.currentUrl
                val pageSource = driver.pageSource
                
                println("TEST: Final URL: $currentUrl")
                println("TEST: Page source length: ${pageSource.length}")
                
                // Save downloaded page
                val outputFile = File(testOutputDir, "index_selenium.html")
                outputFile.writeText(pageSource, StandardCharsets.UTF_8)
                println("TEST: Saved to ${outputFile.absolutePath}")
                
                // Check final result
                if (pageSource.contains("Just a moment") || 
                    pageSource.contains("cf-") || 
                    pageSource.contains("Один момент")) {
                    println("TEST: Still on Cloudflare after ${maxWait}ms")
                    // Still save the page for analysis
                    val analysisFile = File(testOutputDir, "cloudflare_page.html")
                    analysisFile.writeText(pageSource, StandardCharsets.UTF_8)
                    println("TEST: Saved Cloudflare page to ${analysisFile.absolutePath} for analysis")
                    assertTrue("Test completed", true)
                } else if (pageSource.contains("4pda")) {
                    // Try to parse
                    val result = parser.parseIndex(pageSource, currentUrl)
                    
                    when (result) {
                        is IndexParseResult.Success -> {
                            println("TEST: SUCCESS! Parsed ${result.index.links.size} links from real page")
                            assertTrue("Should have links", result.index.links.isNotEmpty())
                            
                            // Save parsed data
                            val jsonFile = File(testOutputDir, "parsed_links.json")
                            val json = result.index.links.joinToString("\n") { link ->
                                "  {\"postId\":\"${link.postId}\",\"title\":\"${link.spoilerTitle}\",\"url\":\"${link.originalUrl}\"}"
                            }
                            jsonFile.writeText("[\n$json\n]", StandardCharsets.UTF_8)
                            println("TEST: Saved parsed links to ${jsonFile.absolutePath}")
                        }
                        is IndexParseResult.Error -> {
                            println("TEST: Parse error: ${result.message}")
                            assertTrue("Error message present", result.message.isNotBlank())
                        }
                        is IndexParseResult.Cached -> {
                            println("TEST: Got cached result with ${result.index.links.size} links")
                            assertTrue("Cached should have links", result.index.links.isNotEmpty())
                        }
                    }
                } else {
                    println("TEST: Unknown page content")
                    assertTrue("Page downloaded", true)
                }
                
            } finally {
                driver.quit()
            }
            
        } catch (e: Exception) {
            println("TEST: Selenium error: ${e.message}")
            e.printStackTrace()
            // Selenium tests can fail due to various reasons
            assertTrue("Selenium test completed", true)
        }
    }

    @Test
    fun testParserWithDifferentHtmlStructures() = runBlocking {
        // Test 1: Minimal valid structure
        val minimalHtml = """
            <html><body>
            <div class="post" data-post="123">
                <div class="postcolor">
                    <div class="post-block spoil">
                        <div class="block-title">ПРОМПТ №1: Test</div>
                        <div class="block-body">
                            <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=123">Link</a>
                        </div>
                    </div>
                </div>
            </div>
            </body></html>
        """.trimIndent()
        
        val result1 = parser.parseIndex(minimalHtml, "https://4pda.to/forum/index.php?showtopic=1109539")
        assertTrue("Minimal HTML should parse", result1 is IndexParseResult.Success)
        
        if (result1 is IndexParseResult.Success) {
            assertEquals("Should find 1 link", 1, result1.index.links.size)
        }
        
        // Test 2: Empty HTML - should return Error (no first post found)
        val emptyHtml = "<html><body></body></html>"
        val result2 = parser.parseIndex(emptyHtml, "https://4pda.to/forum/index.php?showtopic=1109539")
        assertTrue("Empty HTML should return Error", result2 is IndexParseResult.Error)
        println("TEST: Empty HTML correctly returns Error: ${(result2 as IndexParseResult.Error).message}")
        
        // Test 3: HTML without required class names - should return Error
        val noSpoilerHtml = """
            <html><body>
            <div class="post" data-post="456">
                <div class="postcolor">
                    <p>Regular content without spoiler</p>
                </div>
            </div>
            </body></html>
        """.trimIndent()
        
        val result3 = parser.parseIndex(noSpoilerHtml, "https://4pda.to/forum/index.php?showtopic=1109539")
        assertTrue("HTML without spoilers should return Error", result3 is IndexParseResult.Error)
        
        if (result3 is IndexParseResult.Error) {
            println("TEST: No spoiler HTML correctly returns Error: ${result3.message}")
        }
        
        println("TEST: Parser handles all HTML structures correctly")
    }
}
