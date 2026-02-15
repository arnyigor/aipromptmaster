package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.model.PromptContentMap
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.model.PromptMetadata
import com.arny.aiprompts.data.model.PromptVariant
import com.arny.aiprompts.data.model.Rating
import com.arny.aiprompts.data.model.VariantId
import com.arny.aiprompts.domain.model.Author
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/**
 * UseCase for importing parsed prompts from scraper output files.
 * Reads JSON files from .aiprompts/parsed_prompts directory and exports them
 * to the project's prompts/{category}/ directory.
 */
class ImportParsedPromptsUseCase(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
) {
    companion object {
        private const val PARSED_PROMPTS_DIR = ".aiprompts/parsed_prompts"
        private const val PROJECT_ROOT_DIR = "prompts"
    }

    /**
     * Result of import operation.
     */
    data class ImportResult(
        val success: Boolean,
        val totalFiles: Int,
        val totalPrompts: Int,
        val importedCount: Int,
        val skippedCount: Int,
        val errors: List<String>,
        val categoryBreakdown: Map<String, Int>
    )

    /**
     * Progress of import operation.
     */
    data class ImportProgress(
        val currentFile: String,
        val processedFiles: Int,
        val totalFiles: Int,
        val currentPrompts: Int
    )

    /**
     * Information about an available import file.
     */
    data class ImportFileInfo(
        val fileName: String,
        val category: String,
        val promptCount: Int,
        val filePath: String
    )

    /**
     * Get list of available import files from parsed_prompts directory.
     * Returns Flow with progress updates.
     */
    fun getAvailableFiles(): Flow<ImportProgress> = flow {
        val parsedDir = File(System.getProperty("user.home"), PARSED_PROMPTS_DIR)

        if (!parsedDir.exists()) {
            emit(ImportProgress("", 0, 0, 0))
            return@flow
        }

        val filesArray: Array<File>? = parsedDir.listFiles { file ->
            file.extension == "json" &&
            file.name != "index.json" &&
            !file.name.startsWith("processed_")
        }
        val jsonFiles: List<File> = filesArray?.sortedBy { it.name } ?: emptyList()

        emit(ImportProgress("Found ${jsonFiles.size} files", 0, jsonFiles.size, 0))

        var totalPrompts = 0
        val jsonFilesList = jsonFiles // Create a stable reference

        for ((index, file) in jsonFilesList.withIndex()) {
            try {
                val count = countPromptsInFile(file)
                totalPrompts += count
                emit(ImportProgress(file.name, index + 1, jsonFilesList.size, totalPrompts))
            } catch (e: Exception) {
                emit(ImportProgress("Error: ${e.message}", index + 1, jsonFilesList.size, totalPrompts))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get list of available import files (non-flow version).
     */
    fun getAvailableFilesList(): List<ImportFileInfo> {
        val parsedDir = File(System.getProperty("user.home"), PARSED_PROMPTS_DIR)

        if (!parsedDir.exists()) return emptyList()

        val filesArray: Array<File>? = parsedDir.listFiles { file ->
            file.extension == "json" &&
            file.name != "index.json" &&
            !file.name.startsWith("processed_")
        }

        if (filesArray == null) return emptyList()

        val result = mutableListOf<ImportFileInfo>()

        for (file in filesArray) {
            try {
                val count = countPromptsInFile(file)
                val category = extractCategoryFromFile(file)
                result.add(
                    ImportFileInfo(
                        fileName = file.name,
                        category = category,
                        promptCount = count,
                        filePath = file.absolutePath
                    )
                )
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }

        return result.sortedBy { it.category }
    }

    /**
     * Import selected files and save to prompts/{category}/ directory.
     * Returns Flow with progress updates.
     */
    fun importFiles(filePaths: List<String>): Flow<ImportProgress> = flow {
        if (filePaths.isEmpty()) {
            emit(ImportProgress("No files selected", 0, 1, 0))
            return@flow
        }

        val projectRoot = File(PROJECT_ROOT_DIR)
        if (!projectRoot.exists()) {
            projectRoot.mkdirs()
        }

        val totalFiles = filePaths.size

        for ((index, filePath) in filePaths.withIndex()) {
            val file = File(filePath)
            emit(ImportProgress(file.name, index, totalFiles, 0))

            try {
                val count = importSingleFile(file, projectRoot)
                emit(ImportProgress(file.name, index + 1, totalFiles, count))
            } catch (e: Exception) {
                emit(ImportProgress("Error: ${e.message}", index + 1, totalFiles, 0))
            }
        }

        emit(ImportProgress("Completed", totalFiles, totalFiles, totalFiles))
    }.flowOn(Dispatchers.IO)

    /**
     * Import a single file and save prompts to appropriate category directories.
     * Returns the number of prompts imported.
     */
    private fun importSingleFile(
        sourceFile: File,
        projectRoot: File
    ): Int {
        val content = sourceFile.readText()
        val exportData = json.decodeFromString(CategoryExport.serializer(), content)

        // Get project root directory (parent of prompts/)
        val promptsRoot = File(PROJECT_ROOT_DIR)
        if (!promptsRoot.exists()) {
            promptsRoot.mkdirs()
        }

        var importedCount = 0

        // Process each prompt
        for (exportedPrompt in exportData.prompts) {
            try {
                val category = exportedPrompt.category ?: "general"
                val categoryDir = File(promptsRoot, category)
                if (!categoryDir.exists()) {
                    categoryDir.mkdirs()
                }

                // Generate new UUID for the prompt
                val newPromptId = uuid4().toString()
                val timestamp = Instant.now().atZone(ZoneOffset.UTC).toString()

                // Create PromptJson with prompt_variants
                val promptJson = createPromptJson(exportedPrompt, newPromptId, timestamp)

                // Check if prompt with same title exists (simple deduplication)
                val existingFile = findExistingPromptByTitle(categoryDir, exportedPrompt.title)
                if (existingFile != null) {
                    continue // Skip duplicates
                }

                // Save to file
                val outputFile = File(categoryDir, "$newPromptId.json")
                val jsonContent = json.encodeToString(PromptJson.serializer(), promptJson)
                outputFile.writeText(jsonContent)

                importedCount++

            } catch (e: Exception) {
                // Log error but continue processing
            }
        }

        return importedCount
    }

    /**
     * Create PromptJson with prompt_variants from ExportedPrompt.
     */
    private fun createPromptJson(
        exported: ExportedPrompt,
        promptId: String,
        timestamp: String
    ): PromptJson {
        // Generate UUID for variant
        val variantId = uuid4().toString()

        return PromptJson(
            id = promptId,
            sourceId = exported.postId,
            title = exported.title,
            version = "1.0.0",
            status = "active",
            isLocal = true,
            isFavorite = false,
            description = exported.description ?: "",
            content = mapOf(
                "ru" to (exported.content ?: ""),
                "en" to ""
            ),
            compatibleModels = emptyList(),
            category = exported.category ?: "general",
            tags = exported.tags,
            variables = emptyList(),
            metadata = PromptMetadata(
                author = Author(
                    id = "",
                    name = "4pda User"
                ),
                source = exported.sourceUrl ?: "",
                notes = "Imported from 4pda scraper"
            ),
            rating = Rating(),
            promptVariants = listOf(
                PromptVariant(
                    variantId = VariantId(
                        type = "prompt",
                        id = variantId
                    ),
                    content = PromptContentMap(
                        ru = exported.content ?: "",
                        en = ""
                    ),
                    priority = 1
                )
            ),
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    /**
     * Find existing prompt file by title (for deduplication).
     */
    private fun findExistingPromptByTitle(categoryDir: File, title: String): File? {
        if (!categoryDir.exists()) return null

        val files = categoryDir.listFiles { file ->
            file.extension == "json"
        }

        if (files == null) return null

        for (file in files) {
            try {
                val content = file.readText()
                val promptJson = json.decodeFromString(PromptJson.serializer(), content)
                if (promptJson.title == title) {
                    return file
                }
            } catch (e: Exception) {
                // Skip unreadable files
            }
        }

        return null
    }

    /**
     * Count prompts in a file without fully parsing them.
     */
    private fun countPromptsInFile(file: File): Int {
        return try {
            val content = file.readText()
            val exportData = json.decodeFromString(CategoryExport.serializer(), content)
            exportData.prompts.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Extract category from file content.
     */
    private fun extractCategoryFromFile(file: File): String {
        return try {
            val content = file.readText()
            val exportData = json.decodeFromString(CategoryExport.serializer(), content)
            exportData.category.ifBlank { "general" }
        } catch (e: Exception) {
            "general"
        }
    }

    @Serializable
    data class CategoryExport(
        val version: String = "1.0",
        val exportedAt: String = "",
        val category: String = "",
        val tags: List<String> = emptyList(),
        val totalPrompts: Int = 0,
        val prompts: List<ExportedPrompt> = emptyList()
    )

    @Serializable
    data class ExportedPrompt(
        val id: String,
        val postId: String,
        val title: String,
        val content: String? = null,
        val description: String? = null,
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val sourceUrl: String? = null,
        val parsedAt: String = ""
    )
}
