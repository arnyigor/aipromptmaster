package com.arny.aiprompts.domain.analysis

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Exports parsed prompts to category-based JSON files.
 * Creates structured output organized by 4pda forum categories.
 */
class CategoryPromptExporter(
    private val outputDir: File = File(
        System.getProperty("user.home"),
        ".aiprompts/parsed_prompts"
    )
) {
    
    companion object {
        private const val VERSION = "1.0"
        private val ISO_FORMAT = DateTimeFormatter.ISO_INSTANT
    }

    init {
        outputDir.mkdirs()
    }

    /**
     * Export results by category.
     */
    data class ExportResult(
        val success: Boolean,
        val totalPrompts: Int,
        val byCategory: Map<String, CategoryFileInfo>,
        val errors: List<String>
    )

    /**
     * Info about exported category file.
     */
    data class CategoryFileInfo(
        val category: String,
        val filename: String,
        val path: String,
        val promptCount: Int
    )

    /**
     * Result of parsing a single prompt.
     */
    data class ParsedPromptResult(
        val success: Boolean,
        val postId: String?,
        val prompt: ExportedPrompt?,
        val error: String? = null
    )

    /**
     * Exported prompt data (serializable).
     */
    @Serializable
    data class ExportedPrompt(
        val id: String,
        val postId: String,
        val title: String,
        val content: String? = null,
        val description: String? = null,
        val category: String? = null,
        val tags: List<String>,
        val sourceUrl: String,
        val sourcePage: Int? = null,
        val parsedAt: String,
        val attachments: List<ExportedAttachment> = emptyList()
    )

    @Serializable
    data class ExportedAttachment(
        val name: String,
        val url: String? = null,
        val type: String = "unknown"
    )

    /**
     * Export data for a single category.
     */
    @Serializable
    data class CategoryExport(
        val version: String,
        val exportedAt: String,
        val category: String,
        val tags: List<String>,
        val totalPrompts: Int,
        val prompts: List<ExportedPrompt>
    )

    /**
     * Combined export of all prompts.
     */
    @Serializable
    data class CombinedExport(
        val version: String,
        val exportedAt: String,
        val totalPrompts: Int,
        val prompts: List<ExportedPrompt>
    )

    /**
     * Master index file.
     */
    @Serializable
    data class MasterIndex(
        val version: String,
        val lastUpdated: String,
        val totalPrompts: Int,
        val categories: List<MasterCategory>,
        val tags: List<String>
    )

    @Serializable
    data class MasterCategory(
        val name: String,
        val slug: String,
        val filename: String,
        val promptCount: Int
    )

    /**
     * Export prompts grouped by category.
     * 
     * @param parsedPrompts List of parsed prompt results
     * @param includeContent Whether to include full prompt content
     * @return ExportResult with statistics
     */
    fun exportByCategory(
        parsedPrompts: List<ParsedPromptResult>,
        includeContent: Boolean = true
    ): ExportResult {
        val errors = mutableListOf<String>()
        val byCategory = mutableMapOf<String, MutableList<ExportedPrompt>>()
        var processedCount = 0

        for (result in parsedPrompts) {
            if (!result.success || result.prompt == null) {
                errors.add("Failed: ${result.postId ?: "unknown"} - ${result.error}")
                continue
            }

            val prompt = result.prompt
            val category = prompt.category ?: "Unknown"
            
            byCategory.getOrPut(category) { mutableListOf() }
                .add(prompt)
            
            processedCount++
        }

        // Create category files
        val categoryFiles = mutableListOf<CategoryFileInfo>()
        val timestamp = ISO_FORMAT.format(Instant.now())

        for ((category, prompts) in byCategory) {
            val filename = "${CategoryTagMapper.categoryToSlug(category)}_$timestamp.json"
            val file = File(outputDir, filename)
            
            try {
                val exportData = CategoryExport(
                    version = VERSION,
                    exportedAt = timestamp,
                    category = category,
                    tags = CategoryTagMapper.getTagsForCategory(category),
                    totalPrompts = prompts.size,
                    prompts = prompts
                )
                
                val json = kotlinx.serialization.json.Json {
                    prettyPrint = true
                    encodeDefaults = true
                }.encodeToString(CategoryExport.serializer(), exportData)
                
                file.writeText(json, StandardCharsets.UTF_8)
                
                categoryFiles.add(
                    CategoryFileInfo(
                        category = category,
                        filename = filename,
                        path = file.absolutePath,
                        promptCount = prompts.size
                    )
                )
                
            } catch (e: Exception) {
                errors.add("Failed to write $filename: ${e.message}")
            }
        }

        // Update master index
        updateMasterIndex(categoryFiles, processedCount, errors)

        return ExportResult(
            success = errors.isEmpty() || processedCount > 0,
            totalPrompts = processedCount,
            byCategory = categoryFiles.associateBy { it.category },
            errors = errors
        )
    }

    /**
     * Export all prompts to a single combined file.
     */
    fun exportCombined(
        parsedPrompts: List<ParsedPromptResult>,
        filename: String = "all_prompts_combined.json",
        includeContent: Boolean = true
    ): File? {
        val validPrompts = parsedPrompts
            .filter { it.success && it.prompt != null }
            .map { it.prompt!! }

        if (validPrompts.isEmpty()) return null

        val timestamp = ISO_FORMAT.format(Instant.now())
        val exportData = CombinedExport(
            version = VERSION,
            exportedAt = timestamp,
            totalPrompts = validPrompts.size,
            prompts = validPrompts
        )

        val file = File(outputDir, filename)
        try {
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                encodeDefaults = true
            }.encodeToString(CombinedExport.serializer(), exportData)
            
            file.writeText(json, StandardCharsets.UTF_8)
            return file
            
        } catch (e: Exception) {
            println("Failed to write combined export: ${e.message}")
            return null
        }
    }

    /**
     * Update master index file.
     */
    private fun updateMasterIndex(
        categoryFiles: List<CategoryFileInfo>,
        totalPrompts: Int,
        errors: MutableList<String>
    ) {
        val timestamp = ISO_FORMAT.format(Instant.now())
        
        val indexData = MasterIndex(
            version = VERSION,
            lastUpdated = timestamp,
            totalPrompts = totalPrompts,
            categories = categoryFiles.map { MasterCategory(
                name = it.category,
                slug = CategoryTagMapper.categoryToSlug(it.category),
                filename = it.filename,
                promptCount = it.promptCount
            )},
            tags = CategoryTagMapper.getAllTags().toList()
        )

        val indexFile = File(outputDir, "index.json")
        try {
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                encodeDefaults = true
            }.encodeToString(MasterIndex.serializer(), indexData)
            
            indexFile.writeText(json, StandardCharsets.UTF_8)
            
        } catch (e: Exception) {
            errors.add("Failed to update index: ${e.message}")
        }
    }

    /**
     * Get export statistics.
     */
    fun getStats(): ExportStats {
        val files = outputDir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }
            ?.filter { it.lastModified() > 0 } ?: emptyList()
        
        val indexFile = File(outputDir, "index.json")
        var totalPrompts = 0
        var categories = 0
        
        if (indexFile.exists()) {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val index = json.decodeFromString(MasterIndex.serializer(), indexFile.readText())
                totalPrompts = index.totalPrompts
                categories = index.categories.size
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        return ExportStats(
            outputDir = outputDir.absolutePath,
            categoryFiles = files.size,
            totalPrompts = totalPrompts,
            categories = categories
        )
    }

    data class ExportStats(
        val outputDir: String,
        val categoryFiles: Int,
        val totalPrompts: Int,
        val categories: Int
    )
}
