package com.arny.aiprompts.integration

import com.arny.aiprompts.domain.analysis.HtmlStructureAnalyzer
import com.arny.aiprompts.domain.analysis.StructureReporter
import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.model.IndexParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Simple integration tests for Index Scraping.
 * Tests parsing, structure analysis, and data validation.
 */
class IndexScrapingSimpleTests {

    @Test
    fun testParseSampleHtml() = runBlocking {
        val html = createSampleHtml()
        val parser = IndexParser()
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")

        assertTrue("Should be Success", result is IndexParseResult.Success)
        val links = (result as IndexParseResult.Success).index.links
        
        assertEquals("Should find 3 links", 3, links.size)
        println("TEST: Found ${links.size} links")
    }

    @Test
    fun testLinkStructure() = runBlocking {
        val html = createSampleHtml()
        val parser = IndexParser()
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")

        assertTrue(result is IndexParseResult.Success)
        
        (result as IndexParseResult.Success).index.links.forEach { link ->
            assertTrue("Should have postId", link.postId.isNotBlank())
            assertTrue("Should be 4pda URL", link.originalUrl.contains("4pda.to"))
        }
        
        println("TEST: All links have valid structure")
    }

    @Test
    fun testStructureAnalysis() = runBlocking {
        val html = createSampleHtml()
        val analyzer = HtmlStructureAnalyzer()
        
        val postHtml = extractFirstPostHtml(html)
        val result = analyzer.analyze(postHtml)
        
        assertTrue("Should be valid", result.isValid)
        assertEquals("Should find 4 spoilers (nested counted)", 4, result.structure.spoilerCount)
        
        println("TEST: Found ${result.structure.spoilerCount} spoilers")
    }

    @Test
    fun testReportGeneration() = runBlocking {
        val html = createSampleHtml()
        val analyzer = HtmlStructureAnalyzer()
        
        val postHtml = extractFirstPostHtml(html)
        val result = analyzer.analyze(postHtml)
        
        val markdown = StructureReporter.toMarkdown(result)
        
        assertTrue("Should contain title", markdown.contains("HTML Structure Analysis"))
        assertTrue("Should contain spoilers", markdown.contains("Spoilers"))
        
        println("TEST: Report generated successfully")
    }

    @Test
    fun testIndexStatistics() = runBlocking {
        val html = createSampleHtml()
        val parser = IndexParser()
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")

        assertTrue(result is IndexParseResult.Success)
        val stats = parser.getIndexStats((result as IndexParseResult.Success).index)
        
        assertEquals("Should have 3 total links", 3, stats.totalLinks)
        assertEquals("Should have 1 unique category", 1, stats.uniqueCategories)
        
        println("TEST: Stats calculated: ${stats.totalLinks} links, ${stats.uniqueCategories} categories")
    }

    // ========== Helpers ==========

    private fun createSampleHtml(): String {
        return """
            <!DOCTYPE html>
            <html><head><title>Test</title></head>
            <body>
                <div class="post" data-post="12345">
                    <div class="postcolor">
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №1: Test</div>
                            <div class="block-body">
                                Content 1
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138452835">Link 1</a>
                            </div>
                        </div>
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №2: Test 2</div>
                            <div class="block-body">
                                Content 2
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138456113">Link 2</a>
                            </div>
                        </div>
                        <div class="post-block spoil">
                            <div class="block-title">Category</div>
                            <div class="block-body">
                                <div class="post-block spoil">
                                    <div class="block-title">ПРОМПТ №3: Nested</div>
                                    <div class="block-body">
                                        Content 3
                                        <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138460000">Link 3</a>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun extractFirstPostHtml(html: String): String {
        val start = html.indexOf("""<div class="post" """)
        if (start == -1) return ""
        
        var depth = 0
        var end = start
        
        for (i in start until html.length) {
            val substring = html.substring(i)
            if (substring.startsWith("<div")) depth++
            if (substring.startsWith("</div>")) {
                depth--
                if (depth == 0) {
                    end = i + 6
                    break
                }
            }
        }
        
        return html.substring(start, end)
    }
}
