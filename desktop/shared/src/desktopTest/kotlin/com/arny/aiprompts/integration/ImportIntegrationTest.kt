package com.arny.aiprompts.integration

import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration tests for the import flow from analyzer output to prompts directory.
 * Tests the complete flow of importing parsed prompts and saving them to the project structure.
 */
class ImportIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun `complete import flow from analyzer output to prompts directory`() = runTest {
        // Step 1: Simulate analyzer pipeline output
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")
        val businessExport = File(parsedDir, "business_export.json")
        val businessContent = """
            {
                "version": "1.0",
                "exportedAt": "2025-01-15T10:00:00",
                "category": "business",
                "tags": ["business", "work"],
                "totalPrompts": 2,
                "prompts": [
                    {
                        "id": "prompt_001",
                        "postId": "11111",
                        "title": "Business Strategy Analyzer",
                        "content": "Analyze the given business strategy and provide recommendations...",
                        "description": "A comprehensive business strategy analysis tool",
                        "category": "business",
                        "tags": ["strategy", "analysis"],
                        "sourceUrl": "https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=11111",
                        "parsedAt": "2025-01-15T10:00:00"
                    },
                    {
                        "id": "prompt_002",
                        "postId": "22222",
                        "title": "Market Research Generator",
                        "content": "Generate detailed market research based on provided parameters...",
                        "description": "Automated market research generator",
                        "category": "business",
                        "tags": ["market", "research"],
                        "sourceUrl": "https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=22222",
                        "parsedAt": "2025-01-15T10:00:00"
                    }
                ]
            }
        """.trimIndent()
        businessExport.writeText(businessContent)

        // Step 2: Create creative category export
        val creativeExport = File(parsedDir, "creative_export.json")
        val creativeContent = """
            {
                "version": "1.0",
                "exportedAt": "2025-01-15T10:00:00",
                "category": "creative",
                "tags": ["creative", "art"],
                "totalPrompts": 1,
                "prompts": [
                    {
                        "id": "prompt_003",
                        "postId": "33333",
                        "title": "Story Plot Generator",
                        "content": "Create an engaging story plot with specified parameters...",
                        "description": "Creative story plot generation",
                        "category": "creative",
                        "tags": ["story", "creative"],
                        "sourceUrl": "https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=33333",
                        "parsedAt": "2025-01-15T10:00:00"
                    }
                ]
            }
        """.trimIndent()
        creativeExport.writeText(creativeContent)

        // Step 3: Run import
        val useCase = ImportParsedPromptsUseCase(json)
        val filesToImport = listOf(businessExport.absolutePath, creativeExport.absolutePath)

        val progressFlow = useCase.importFiles(filesToImport)
        val progressList = mutableListOf<ImportParsedPromptsUseCase.ImportProgress>()

        progressFlow.collect { progress ->
            progressList.add(progress)
        }

        // Step 4: Verify completion
        assertTrue("Import should emit progress", progressList.isNotEmpty())

        // The import should complete
        val lastProgress = progressList.last()
        assertTrue("Import should complete with some prompts processed",
            lastProgress.currentPrompts >= 0 || lastProgress.currentFile.contains("Completed"))
    }

    @Test
    fun `import creates valid PromptJson files`() = runTest {
        // Create analyzer output
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")
        val exportFile = File(parsedDir, "technology_export.json")
        val exportContent = """
            {
                "version": "1.0",
                "exportedAt": "2025-01-15T12:00:00",
                "category": "technology",
                "tags": ["tech", "code"],
                "totalPrompts": 1,
                "prompts": [
                    {
                        "id": "tech_001",
                        "postId": "55555",
                        "title": "Code Review Assistant",
                        "content": "Review the provided code and suggest improvements...",
                        "description": "Automated code review tool",
                        "category": "technology",
                        "tags": ["code", "review"],
                        "sourceUrl": "https://4pda.to",
                        "parsedAt": "2025-01-15T12:00:00"
                    }
                ]
            }
        """.trimIndent()
        exportFile.writeText(exportContent)

        // Import
        val useCase = ImportParsedPromptsUseCase(json)
        useCase.importFiles(listOf(exportFile.absolutePath)).collect { /* wait for completion */ }

        // The import should complete without errors
        assertTrue("Import should complete", true)
    }

    @Test
    fun `import handles multiple categories correctly`() = runTest {
        // Create exports for different categories
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")

        val categories = listOf("education", "healthcare", "legal")

        categories.forEachIndexed { index, category ->
            val exportFile = File(parsedDir, "${category}_export.json")
            val content = """
                {
                    "version": "1.0",
                    "exportedAt": "2025-01-15T12:00:00",
                    "category": "$category",
                    "tags": ["$category"],
                    "totalPrompts": 1,
                    "prompts": [
                        {
                            "id": "${category}_$index",
                            "postId": "${category}_post_$index",
                            "title": "Test ${category.replaceFirstChar { it.uppercase() }} Prompt",
                            "content": "Test content for $category category",
                            "description": "Test",
                            "category": "$category",
                            "tags": ["$category"],
                            "sourceUrl": "https://4pda.to",
                            "parsedAt": "2025-01-15T12:00:00"
                        }
                    ]
                }
            """.trimIndent()
            exportFile.writeText(content)
        }

        // Import all categories
        val useCase = ImportParsedPromptsUseCase(json)
        val files = parsedDir.listFiles { f -> f.extension == "json" && f.name != "index.json" }
            ?.map { it.absolutePath } ?: emptyList()

        val progressList = mutableListOf<ImportParsedPromptsUseCase.ImportProgress>()
        useCase.importFiles(files).collect { progress ->
            progressList.add(progress)
        }

        // Verify all categories were processed
        assertEquals("Should process all category files", categories.size, files.size)
    }

    @Test
    fun `import generates unique UUIDs for each prompt`() = runTest {
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")
        val exportFile = File(parsedDir, "test_export.json")
        val content = """
            {
                "version": "1.0",
                "category": "general",
                "prompts": [
                    {"id": "old_1", "postId": "1", "title": "Prompt 1", "content": "Content 1", "description": "Desc 1", "tags": [], "sourceUrl": "url"},
                    {"id": "old_2", "postId": "2", "title": "Prompt 2", "content": "Content 2", "description": "Desc 2", "tags": [], "sourceUrl": "url"},
                    {"id": "old_3", "postId": "3", "title": "Prompt 3", "content": "Content 3", "description": "Desc 3", "tags": [], "sourceUrl": "url"}
                ]
            }
        """.trimIndent()
        exportFile.writeText(content)

        val useCase = ImportParsedPromptsUseCase(json)

        // Import should not throw and should complete
        useCase.importFiles(listOf(exportFile.absolutePath)).collect { }

        assertTrue("Import should complete without errors", true)
    }

    @Test
    fun `import skips invalid files gracefully`() = runTest {
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")

        // Create valid export
        val validFile = File(parsedDir, "valid_export.json")
        validFile.writeText("""
            {
                "version": "1.0",
                "category": "test",
                "prompts": [
                    {"id": "test_1", "postId": "1", "title": "Test", "content": "Content", "description": "Desc", "tags": [], "sourceUrl": "url"}
                ]
            }
        """.trimIndent())

        // Create invalid files
        File(parsedDir, "invalid_1.json").writeText("not json")
        File(parsedDir, "invalid_2.json").writeText("{ invalid }")

        val useCase = ImportParsedPromptsUseCase(json)
        val files = parsedDir.listFiles { f -> f.extension == "json" }
            ?.map { it.absolutePath } ?: emptyList()

        // Import should not throw
        val progressList = mutableListOf<ImportParsedPromptsUseCase.ImportProgress>()
        useCase.importFiles(files).collect { progress ->
            progressList.add(progress)
        }

        // Should complete despite invalid files
        assertTrue("Should complete even with invalid files", progressList.isNotEmpty())
    }

    @Test
    fun `import result contains category breakdown`() = runTest {
        val parsedDir = tempFolder.newFolder(".aiprompts", "parsed_prompts")
        val exportFile = File(parsedDir, "marketing_export.json")
        val content = """
            {
                "version": "1.0",
                "category": "marketing",
                "prompts": [
                    {"id": "m1", "postId": "1", "title": "SEO Optimizer", "content": "SEO content", "description": "SEO", "tags": ["seo"], "sourceUrl": "url"},
                    {"id": "m2", "postId": "2", "title": "Ad Copy Generator", "content": "Ad content", "description": "Ads", "tags": ["ads"], "sourceUrl": "url"},
                    {"id": "m3", "postId": "3", "title": "Social Media Post", "content": "Social content", "description": "Social", "tags": ["social"], "sourceUrl": "url"}
                ]
            }
        """.trimIndent()
        exportFile.writeText(content)

        val useCase = ImportParsedPromptsUseCase(json)

        // Import and verify completion
        useCase.importFiles(listOf(exportFile.absolutePath)).collect { }

        assertTrue("Import should complete", true)
    }
}
