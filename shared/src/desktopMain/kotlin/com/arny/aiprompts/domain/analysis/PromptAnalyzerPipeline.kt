package com.arny.aiprompts.domain.analysis

import com.arny.aiprompts.domain.analysis.CategoryPromptExporter.ExportResult
import com.arny.aiprompts.domain.analysis.CategoryPromptExporter.ParsedPromptResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Main pipeline for analyzing and parsing prompts from 4pda forum.
 * Implements IAnalyzerPipeline interface for KMP compatibility.
 *
 * Pipeline stages:
 * 1. Load index from CSV/JSON
 * 2. Map postId → HTML file
 * 3. Deduplicate (skip already processed)
 * 4. Parse HTML pages
 * 5. Extract tags
 * 6. Export by category
 */
class PromptAnalyzerPipeline(
    private val scrapedPagesDir: File = File(
        System.getProperty("user.home"),
        ".aiprompts/scraped_html"
    ),
    private val outputDir: File = File(
        System.getProperty("user.home"),
        ".aiprompts/parsed_prompts"
    ),
    private val indexFile: File = File(
        System.getProperty("user.home"),
        ".aiprompts/integration_test/index_export.json"
    )
) : IAnalyzerPipeline {

    companion object {
        private val ISO_FORMAT = DateTimeFormatter.ISO_INSTANT
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }

    private val pageParser = PromptPageParser()
    private val exporter = CategoryPromptExporter(outputDir)

    /**
     * Run the full analysis pipeline with progress updates.
     * Returns a Flow that emits progress at each stage.
     * Uses callbackFlow to support concurrent emissions.
     */
    override fun runPipelineFlow(): Flow<AnalyzerPipelineProgress> = callbackFlow {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var newPrompts = 0
        var skippedDuplicates = 0
        var missingPages = 0

        trySend(AnalyzerPipelineProgress.Loading("🚀 Starting Prompt Analyzer Pipeline"))
        trySend(AnalyzerPipelineProgress.Loading("📂 Scraped pages: ${scrapedPagesDir.absolutePath}"))
        trySend(AnalyzerPipelineProgress.Loading("📄 Index file: ${indexFile.absolutePath}"))
        trySend(AnalyzerPipelineProgress.Loading("📁 Output dir: ${outputDir.absolutePath}"))

        // 1. Load index
        trySend(AnalyzerPipelineProgress.Loading("\n📥 Stage 1: Loading index..."))
        val indexEntries = loadIndex()
        if (indexEntries.isEmpty()) {
            trySend(AnalyzerPipelineProgress.Error("Failed to load index: file not found or empty"))
            trySend(AnalyzerPipelineProgress.Completed(AnalyzerPipelineResult(
                success = false,
                totalProcessed = 0,
                newPrompts = 0,
                skippedDuplicates = 0,
                missingPages = 0,
                errors = 1,
                outputFiles = emptyList(),
                durationMs = System.currentTimeMillis() - startTime
            )))
            close()
            return@callbackFlow
        }
        trySend(AnalyzerPipelineProgress.Loading("   Loaded ${indexEntries.size} entries from index"))

        // 2. Map postId → HTML file
        trySend(AnalyzerPipelineProgress.Mapping("\n🔗 Stage 2: Mapping postId to HTML files..."))
        val postIdToFile = mapPostIdToFile(indexEntries)
        trySend(AnalyzerPipelineProgress.Mapping("   Mapped ${postIdToFile.size} postIds to files"))

        // 3. Load already processed
        trySend(AnalyzerPipelineProgress.Deduplicating("\n🔄 Stage 3: Checking already processed..."))
        val processedIds = loadProcessedIds()
        trySend(AnalyzerPipelineProgress.Deduplicating("   Already processed: ${processedIds.size} prompts"))

        // 4. Filter entries
        val entriesToProcess = indexEntries.filter { entry ->
            when {
                entry.postId in processedIds -> {
                    skippedDuplicates++
                    false
                }
                entry.postId !in postIdToFile -> {
                    missingPages++
                    errors.add("Missing page for: ${entry.postId}")
                    false
                }
                else -> true
            }
        }
        trySend(AnalyzerPipelineProgress.Deduplicating("   To process: ${entriesToProcess.size} new prompts"))

        if (entriesToProcess.isEmpty()) {
            trySend(AnalyzerPipelineProgress.Loading("\n⚠️ No new prompts to process"))
            trySend(AnalyzerPipelineProgress.Completed(AnalyzerPipelineResult(
                success = true,
                totalProcessed = 0,
                newPrompts = 0,
                skippedDuplicates = skippedDuplicates,
                missingPages = missingPages,
                errors = errors.size,
                outputFiles = emptyList(),
                durationMs = System.currentTimeMillis() - startTime
            )))
            close()
            return@callbackFlow
        }

        // 5. Parse HTML pages
        trySend(AnalyzerPipelineProgress.Parsing(0, entriesToProcess.size, ""))
        val parsedResults = parsePages(entriesToProcess, postIdToFile)

        // Emit progress for each parsed page
        parsedResults.forEachIndexed { index, result ->
            trySend(AnalyzerPipelineProgress.Parsing(index + 1, parsedResults.size, result.postId ?: "unknown"))
        }

        newPrompts = parsedResults.count { it.success && it.prompt != null }

        // 6. Export
        trySend(AnalyzerPipelineProgress.Exporting("\n💾 Stage 5: Exporting to category files..."))
        val exportResult = exporter.exportByCategory(parsedResults, true)
        errors.addAll(exportResult.errors)

        // 7. Save processed IDs
        saveProcessedIds(parsedResults)

        val duration = System.currentTimeMillis() - startTime
        trySend(AnalyzerPipelineProgress.Exporting("\n✅ Pipeline completed in ${duration}ms"))
        trySend(AnalyzerPipelineProgress.Exporting("   New prompts: $newPrompts"))
        trySend(AnalyzerPipelineProgress.Exporting("   Skipped: $skippedDuplicates"))
        trySend(AnalyzerPipelineProgress.Exporting("   Missing pages: $missingPages"))
        trySend(AnalyzerPipelineProgress.Exporting("   Errors: ${errors.size}"))

        trySend(AnalyzerPipelineProgress.Completed(AnalyzerPipelineResult(
            success = errors.isEmpty() || newPrompts > 0,
            totalProcessed = parsedResults.size,
            newPrompts = newPrompts,
            skippedDuplicates = skippedDuplicates,
            missingPages = missingPages,
            errors = errors.size,
            outputFiles = exportResult.byCategory.values.map { it.path },
            durationMs = duration
        )))

        close()
    }

    /**
     * Parse pages in parallel.
     */
    private suspend fun parsePages(
        entries: List<IndexEntry>,
        postIdToFile: Map<String, File>
    ): List<ParsedPromptResult> = coroutineScope {
        entries.map { entry ->
            async(Dispatchers.IO) {
                val file = postIdToFile[entry.postId]
                if (file == null) {
                    ParsedPromptResult(
                        success = false,
                        postId = entry.postId,
                        prompt = null,
                        error = "HTML file not found"
                    )
                } else {
                    parseSinglePage(entry, file)
                }
            }
        }.awaitAll()
    }

    /**
     * Get pipeline statistics.
     */
    override fun getStats(): AnalyzerStats {
        val stats = exporter.getStats()
        val processedFile = File(outputDir, "processed_ids.json")
        var processedCount = 0

        if (processedFile.exists()) {
            try {
                val content = processedFile.readText(StandardCharsets.UTF_8)
                val data = json.decodeFromString(ProcessedIds.serializer(), content)
                processedCount = data.ids.size
            } catch (e: Exception) {
                // Ignore
            }
        }

        return AnalyzerStats(
            scrapedPages = scrapedPagesDir.listFiles { f -> f.name.startsWith("page_") }?.size ?: 0,
            processedPrompts = processedCount,
            exportedPrompts = stats.totalPrompts,
            exportedCategories = stats.categories,
            outputDir = outputDir.absolutePath
        )
    }

    /**
     * Run the full analysis pipeline (blocking version).
     */
    suspend fun runPipeline(): AnalyzerPipelineResult {
        var result: AnalyzerPipelineResult? = null
        runPipelineFlow().collect { progress ->
            when (progress) {
                is AnalyzerPipelineProgress.Completed -> result = progress.result
                else -> { /* ignore intermediate progress */ }
            }
        }
        return result ?: AnalyzerPipelineResult(
            success = false,
            totalProcessed = 0,
            newPrompts = 0,
            skippedDuplicates = 0,
            missingPages = 0,
            errors = 1,
            outputFiles = emptyList(),
            durationMs = 0
        )
    }

    /**
     * Load index entries from JSON file.
     */
    private fun loadIndex(): List<IndexEntry> {
        if (!indexFile.exists()) {
            println("   ⚠️ Index file not found: ${indexFile.absolutePath}")
            return emptyList()
        }

        return try {
            val jsonContent = indexFile.readText(StandardCharsets.UTF_8)
            val data = json.decodeFromString(IndexWrapper.serializer(), jsonContent)
            data.categories.flatMap { category ->
                category.prompts.map { prompt ->
                    IndexEntry(
                        category = category.name,
                        title = prompt.title,
                        postId = prompt.postId,
                        url = prompt.url
                    )
                }
            }
        } catch (e: Exception) {
            println("   ⚠️ Failed to parse index: ${e.message}")
            // Try CSV format as fallback
            loadFromCsv()
        }
    }

    /**
     * Fallback: load from CSV.
     */
    private fun loadFromCsv(): List<IndexEntry> {
        val csvFile = File(indexFile.parentFile, "index_export.csv")
        if (!csvFile.exists()) return emptyList()

        return try {
            val lines = csvFile.readLines()
            if (lines.size <= 1) return emptyList()

            lines.drop(1).mapNotNull { line ->
                val parts = parseCsvLine(line)
                if (parts.size >= 4) {
                    IndexEntry(
                        category = parts[0],
                        title = parts[1],
                        postId = parts[2],
                        url = parts[3]
                    )
                } else null
            }
        } catch (e: Exception) {
            println("   ⚠️ Failed to parse CSV: ${e.message}")
            emptyList()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Map postId to HTML file.
     */
    private fun mapPostIdToFile(entries: List<IndexEntry>): Map<String, File> {
        val map = mutableMapOf<String, File>()

        // First, create reverse index from HTML files
        scrapedPagesDir.listFiles { f -> f.name.startsWith("page_") && f.name.endsWith(".html") }
            ?.forEach { file ->
                val pageNum = file.name
                    .substringAfter("page_")
                    .substringBefore(".html")
                    .toIntOrNull()

                if (pageNum != null) {
                    // Parse the file and extract postIds
                    try {
                        val content = file.readText(StandardCharsets.UTF_8)
                        val postIds = extractPostIdsFromHtml(content)

                        postIds.forEach { postId ->
                            if (!map.containsKey(postId)) {
                                map[postId] = file
                            }
                        }
                    } catch (e: Exception) {
                        // Skip file
                    }
                }
            }

        return map
    }

    /**
     * Extract post IDs from HTML content.
     */
    private fun extractPostIdsFromHtml(html: String): List<String> {
        val pattern = Regex("""data-post="(\d+)"""")
        return pattern.findAll(html)
            .map { it.groupValues[1] }
            .toList()
    }

    /**
     * Load already processed IDs.
     */
    private fun loadProcessedIds(): Set<String> {
        val processedFile = File(outputDir, "processed_ids.json")
        if (!processedFile.exists()) return emptySet()

        return try {
            val content = processedFile.readText(StandardCharsets.UTF_8)
            json.decodeFromString(ProcessedIds.serializer(), content).ids.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Save processed IDs.
     */
    private fun saveProcessedIds(results: List<ParsedPromptResult>) {
        val processedFile = File(outputDir, "processed_ids.json")
        val ids = results
            .filter { it.success && it.prompt != null }
            .mapNotNull { it.postId }

        try {
            val data = ProcessedIds(
                lastUpdated = ISO_FORMAT.format(Instant.now()),
                ids = ids
            )
            val json = json.encodeToString(ProcessedIds.serializer(), data)
            processedFile.writeText(json, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            println("   ⚠️ Failed to save processed IDs: ${e.message}")
        }
    }

    /**
     * Parse a single page.
     */
    private suspend fun parseSinglePage(entry: IndexEntry, file: File): ParsedPromptResult {
        val parseResult = pageParser.parsePage(file, entry.postId)

        if (!parseResult.success) {
            return ParsedPromptResult(
                success = false,
                postId = entry.postId,
                prompt = null,
                error = parseResult.error
            )
        }

        // Generate ID
        val id = generatePromptId(entry.postId)
        val timestamp = ISO_FORMAT.format(Instant.now())

        // Get tags from category and auto-detect
        val tags = CategoryTagMapper.getTagsWithAutoDetect(
            entry.category,
            parseResult.cleanContent ?: ""
        )

        // Extract description from content
        val description = parseResult.cleanContent
            ?.substring(0, minOf(200, parseResult.cleanContent.length))
            ?.replace("\n", " ")
            ?.trim()

        val prompt = CategoryPromptExporter.ExportedPrompt(
            id = id,
            postId = entry.postId,
            title = entry.title,
            content = parseResult.promptContent,
            description = description,
            category = entry.category,
            tags = tags,
            sourceUrl = entry.url,
            sourcePage = extractPageNumber(file.name),
            parsedAt = timestamp,
            attachments = parseResult.attachments.map {
                CategoryPromptExporter.ExportedAttachment(
                    name = it.name,
                    url = it.url,
                    type = it.type.name.lowercase()
                )
            }
        )

        return ParsedPromptResult(
            success = true,
            postId = entry.postId,
            prompt = prompt,
            error = null
        )
    }

    /**
     * Generate unique prompt ID.
     */
    private fun generatePromptId(postId: String): String {
        return "prompt_${postId}"
    }

    /**
     * Extract page number from filename.
     */
    private fun extractPageNumber(filename: String): Int? {
        return filename
            .substringAfter("page_")
            .substringBefore(".html")
            .toIntOrNull()
    }

    @Serializable
    data class IndexEntry(
        val category: String,
        val title: String,
        val postId: String,
        val url: String
    )

    @Serializable
    data class IndexWrapper(
        val topicId: String = "",
        val sourceUrl: String = "",
        val totalLinks: Int = 0,
        val categories: List<CategoryWrapper> = emptyList()
    )

    @Serializable
    data class CategoryWrapper(
        val name: String = "",
        val count: Int = 0,
        val prompts: List<PromptWrapper> = emptyList()
    )

    @Serializable
    data class PromptWrapper(
        val postId: String = "",
        val title: String = "",
        val url: String = ""
    )

    @Serializable
    data class ProcessedIds(
        val lastUpdated: String = "",
        val ids: List<String> = emptyList()
    )
}
