package com.arny.aiprompts.integration

import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.model.IndexParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Export tests for 4pda index data.
 * Extracts categories, prompt titles, and links, then saves to JSON.
 * 
 * These tests require real HTML files in ~/.aiprompts/integration_test/
 * They are ignored if the test files are not present.
 */
class IndexExportTests {

    private val parser = IndexParser()
    private val testOutputDir = File(System.getProperty("user.home"), ".aiprompts/integration_test")
    
    init {
        if (!testOutputDir.exists()) {
            testOutputDir.mkdirs()
        }
    }

    private fun skipIfNoTestFile(fileName: String): Boolean {
        val testFile = File(testOutputDir, fileName)
        if (!testFile.exists()) {
            println("SKIPPED: Test file not found: ${testFile.absolutePath}")
            return true
        }
        return false
    }
    
    @Test
    @Ignore("Requires real HTML file: index_selenium.html in ~/.aiprompts/integration_test/")
    fun testParseAndExportIndexData() = runBlocking {
        val testFile = File(testOutputDir, "index_selenium.html")
        
        if (!testFile.exists()) {
            println("TEST: Test file not found: ${testFile.absolutePath}")
            assertTrue("Test file must exist", false)
            return@runBlocking
        }
        
        val html = testFile.readText(StandardCharsets.UTF_8)
        println("TEST: Loaded ${html.length} chars from ${testFile.name}")
        
        // Parse the index
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")
        
        when (result) {
            is IndexParseResult.Success -> {
                val index = result.index
                println("TEST: SUCCESS! Parsed ${index.links.size} links")
                
                // Get statistics
                val stats = parser.getIndexStats(index)
                println("TEST: Statistics:")
                println("  - Total links: ${stats.totalLinks}")
                println("  - Unique categories: ${stats.uniqueCategories}")
                
                // Group by category
                val byCategory = index.links.groupBy { it.category ?: "Unknown" }
                println("  - Links by category:")
                byCategory.forEach { (cat, links) ->
                    println("    - $cat: ${links.size} links")
                }
                
                // Export to JSON format
                val jsonOutput = buildJsonExport(index)
                
                // Save JSON
                val jsonFile = File(testOutputDir, "index_export.json")
                jsonFile.writeText(jsonOutput, StandardCharsets.UTF_8)
                println("TEST: Saved JSON export to ${jsonFile.absolutePath}")
                
                // Verify export
                assertTrue("Should have links", index.links.isNotEmpty())
                assertTrue("Should have categories", stats.uniqueCategories > 0)
                
                // Print first 5 entries
                println("\nTEST: First 5 entries:")
                index.links.take(5).forEach { link ->
                    println("  - Category: ${link.category}")
                    println("    Title: ${link.spoilerTitle}")
                    println("    URL: ${link.originalUrl}")
                }
            }
            is IndexParseResult.Error -> {
                println("TEST: Parse error: ${result.message}")
                assertTrue("Parsing should succeed", false)
            }
            is IndexParseResult.Cached -> {
                println("TEST: Got cached result")
                val index = result.index
                println("TEST: ${index.links.size} links from cache")
            }
        }
    }

    @Test
    @Ignore("Requires real HTML file: index_selenium.html in ~/.aiprompts/integration_test/")
    fun testExportToCsv() = runBlocking {
        val testFile = File(testOutputDir, "index_selenium.html")
        
        if (!testFile.exists()) {
            println("TEST: Test file not found")
            assertTrue("Test file must exist", false)
            return@runBlocking
        }
        
        val html = testFile.readText(StandardCharsets.UTF_8)
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")
        
        when (result) {
            is IndexParseResult.Success -> {
                val index = result.index
                
                // Build CSV
                val csvOutput = buildString {
                    // Header
                    appendLine("category,title,postId,url")
                    
                    // Rows
                    index.links.forEach { link ->
                        val category = link.category?.replace("\"", "\"\"") ?: ""
                        val title = link.spoilerTitle?.replace("\"", "\"\"") ?: ""
                        val postId = link.postId
                        val url = link.originalUrl
                        
                        appendLine("\"$category\",\"$title\",\"$postId\",\"$url\"")
                    }
                }
                
                // Save CSV
                val csvFile = File(testOutputDir, "index_export.csv")
                csvFile.writeText(csvOutput, StandardCharsets.UTF_8)
                println("TEST: Saved CSV export to ${csvFile.absolutePath}")
                println("TEST: ${index.links.size} rows exported to CSV")
                
                assertTrue("Should have links", index.links.isNotEmpty())
            }
            else -> {
                assertTrue("Parsing should succeed", false)
            }
        }
    }

    @Test
    @Ignore("Requires real HTML file: index_selenium.html in ~/.aiprompts/integration_test/")
    fun testParseOnlyFirstPageLinks() = runBlocking {
        val testFile = File(testOutputDir, "index_selenium.html")
        
        if (!testFile.exists()) {
            assertTrue("Test file must exist", false)
            return@runBlocking
        }
        
        val html = testFile.readText(StandardCharsets.UTF_8)
        val result = parser.parseIndex(html, "https://4pda.to/forum/index.php?showtopic=1109539")
        
        when (result) {
            is IndexParseResult.Success -> {
                val index = result.index
                
                // Filter links that should be on first page (no st parameter)
                val firstPageLinks = index.links.filter { link ->
                    link.pageOffset == null || link.pageOffset == 0
                }
                
                println("TEST: Total links: ${index.links.size}")
                println("TEST: Links without page offset (first page): ${firstPageLinks.size}")
                
                // Get stats
                val stats = parser.getIndexStats(index)
                println("TEST: Unique categories: ${stats.uniqueCategories}")
                
                // Show categories breakdown
                val byCategory = index.links.groupBy { it.category ?: "Unknown" }
                byCategory.forEach { (cat, links) ->
                    println("  $cat: ${links.size} prompts")
                }
                
                assertTrue("Should have links from first page", index.links.isNotEmpty())
            }
            else -> {
                assertTrue("Parsing should succeed", false)
            }
        }
    }

    /**
     * Build JSON export of index data
     */
    private fun buildJsonExport(index: com.arny.aiprompts.domain.index.model.ParsedIndex): String {
        val byCategory = index.links.groupBy { it.category ?: "Unknown" }
        
        return buildString {
            appendLine("{")
            appendLine("  \"topicId\": \"${index.topicId}\",")
            appendLine("  \"sourceUrl\": \"${index.sourceUrl}\",")
            appendLine("  \"totalLinks\": ${index.links.size},")
            appendLine("  \"categories\": [")
            
            val categoryEntries = byCategory.entries.toList()
            categoryEntries.forEachIndexed { idx, (category, links) ->
                appendLine("    {")
                appendLine("      \"name\": \"${escapeJson(category)}\",")
                appendLine("      \"count\": ${links.size},")
                appendLine("      \"prompts\": [")
                
                val promptEntries = links.map { link ->
                    """        {"postId": "${link.postId}", "title": "${escapeJson(link.spoilerTitle)}", "url": "${link.originalUrl}"}"""
                }
                appendLine(promptEntries.joinToString(",\n"))
                
                appendLine("      ]")
                append("    }")
                if (idx < categoryEntries.size - 1) appendLine(",") else appendLine()
            }
            
            appendLine("  ]")
            append("}")
        }
    }
    
    private fun escapeJson(str: String?): String {
        if (str == null) return ""
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
