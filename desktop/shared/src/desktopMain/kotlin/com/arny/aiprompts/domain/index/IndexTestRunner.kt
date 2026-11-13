package com.arny.aiprompts.domain.index

import com.arny.aiprompts.domain.index.model.IndexParseResult
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple runner to test index parsing.
 * Run: ./gradlew :desktopApp:run --args="--test-index"
 */
object IndexTestRunner {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=".repeat(80))
        println("INDEX PARSING TEST")
        println("=".repeat(80))

        val testUrl = "https://4pda.to/forum/index.php?showtopic=1109539"
        val outputDir = File(System.getProperty("user.home"), ".aiprompts/test_output")
        outputDir.mkdirs()

        // Try to download page
        println("\n[1] Downloading index page...")
        val htmlContent = tryDownloadPage(testUrl)

        if (htmlContent != null) {
            // Save downloaded HTML
            val htmlFile = File(outputDir, "index_page_downloaded.html")
            htmlFile.writeText(htmlContent)
            println("✓ Saved to: ${htmlFile.absolutePath}")
            println("  Size: ${htmlContent.length} chars")

            // Parse
            println("\n[2] Parsing index...")
            val parser = IndexParser()
            val result = parser.parseIndex(htmlContent, testUrl)

            when (result) {
                is IndexParseResult.Success -> {
                    val index = result.index
                    println("\n=== PARSING SUCCESS ===")
                    println("Topic ID: ${index.topicId}")
                    println("Total links found: ${index.links.size}")
                    
                    // Stats
                    println("\n--- Statistics ---")
                    val byCategory = index.links.groupBy { it.category ?: "Без категории" }
                    byCategory.forEach { (cat, links) ->
                        println("  $cat: ${links.size} ссылок")
                    }

                    // Show first 10 links
                    println("\n--- First 10 links ---")
                    index.links.take(10).forEachIndexed { i, link ->
                        println("${i + 1}. Post ${link.postId}")
                        println("   Спойлер: ${link.spoilerTitle?.take(60) ?: "N/A"}")
                        println("   URL: ${link.originalUrl.take(70)}...")
                    }

                    // Save parsed index
                    val indexFile = File(outputDir, "parsed_index.json")
                    val indexJson = kotlinx.serialization.json.Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }.encodeToString(com.arny.aiprompts.domain.index.model.ParsedIndex.serializer(), index)
                    indexFile.writeText(indexJson)
                    println("\n✓ Parsed index saved to: ${indexFile.absolutePath}")
                }
                is IndexParseResult.Error -> {
                    println("\n=== PARSING ERROR ===")
                    println("Message: ${result.message}")
                    result.exception?.printStackTrace()
                }
                is IndexParseResult.Cached -> {
                    println("\n=== USING CACHED ===")
                }
            }
        } else {
            println("✗ Failed to download page")
            println("\n[Alternative] Using sample HTML...")

            // Use sample HTML
            val sampleHtml = createSampleHtml()
            val sampleFile = File(outputDir, "index_sample.html")
            sampleFile.writeText(sampleHtml)
            println("✓ Sample saved to: ${sampleFile.absolutePath}")

            val parser = IndexParser()
            val result = parser.parseIndex(sampleHtml, testUrl)

            when (result) {
                is IndexParseResult.Success -> {
                    println("\n=== SAMPLE PARSING SUCCESS ===")
                    println("Total links: ${result.index.links.size}")
                }
                is IndexParseResult.Error -> {
                    println("Error: ${result.message}")
                }
                else -> {}
            }
        }

        println("\n" + "=".repeat(80))
    }

    private fun tryDownloadPage(url: String): String? {
        return try {
            // Try simple HTTP first (no Selenium needed for basic HTML)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                println("HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            println("Download error: ${e.message}")
            null
        }
    }

    private fun createSampleHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body>
                <div class="post" data-post="12345">
                    <div class="postcolor">
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №1: Генератор текста</div>
                            <div class="block-body">
                                Ты - эксперт по написанию текстов...
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138452835">Link 1</a>
                            </div>
                        </div>
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №2: Анализатор кода</div>
                            <div class="block-body">
                                Проанализируй следующий код...
                                <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138456113">Link 2</a>
                            </div>
                        </div>
                        <div class="post-block spoil">
                            <div class="block-title">Категория: Изображения</div>
                            <div class="block-body">
                                <div class="post-block spoil">
                                    <div class="block-title">ПРОМПТ №3: Генератор изображений</div>
                                    <div class="block-body">
                                        Создай красивую картинку...
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
}
