package com.arny.aiprompts.data.scraper

import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.model.IndexLink
import com.arny.aiprompts.domain.index.model.IndexParseResult
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.interfaces.PreScrapeCheck
import com.arny.aiprompts.domain.interfaces.ScraperProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.random.Random

/**
 * Desktop implementation of WebScraper using Selenium.
 * Located in desktopMain - JVM only.
 * 
 * Provides functionality to scrape 4PDA forum pages and parse index.
 */
class DesktopWebScraper : IWebScraper {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val PAGE_LOAD_TIMEOUT_SECONDS = 30L
        private const val MIN_DELAY_MS = 1500L
        private const val MAX_DELAY_MS = 3000L
    }

    override fun getSaveDirectory(): String {
        val dir = File(System.getProperty("user.home"), ".aiprompts/scraped_html")
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun openSaveDirectory() {
        val dir = File(getSaveDirectory())
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
        } else {
            println("Действие 'Открыть директорию' не поддерживается на этой системе.")
        }
    }

    override fun getExistingScrapedFiles(): List<String> {
        return File(getSaveDirectory())
            .listFiles { file ->
                file.isFile && file.name.startsWith("page_") && file.name.endsWith(".html")
            }
            ?.sortedBy { file ->
                file.name.substringAfter("_").substringBefore(".").toIntOrNull() ?: 0
            }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    override fun checkExistingFiles(pages: List<Int>): PreScrapeCheck {
        val saveDir = File(getSaveDirectory())
        val missingPages = mutableListOf<Int>()
        var existingFileCount = 0

        for (pageNumber in pages) {
            val file = File(saveDir, "page_$pageNumber.html")
            if (file.exists()) {
                existingFileCount++
            } else {
                missingPages.add(pageNumber)
            }
        }

        return PreScrapeCheck(existingFileCount, missingPages)
    }

    override fun scrapeAndSave(
        baseUrl: String,
        pagesToScrape: List<Int>
    ): Flow<ScraperProgress> = channelFlow {
        if (pagesToScrape.isEmpty()) {
            send(ScraperProgress.Error("Нет страниц для скачивания."))
            return@channelFlow
        }

        send(
            ScraperProgress.InProgress(
                "Запускаю скачивание для ${pagesToScrape.size} страниц: ${pagesToScrape.map { it }}"
            )
        )

        val saveDir = File(getSaveDirectory())
        send(ScraperProgress.InProgress("Сохранение в директорию: ${saveDir.absolutePath}"))
        send(ScraperProgress.InProgress("Запуск Chrome (через встроенный Selenium Manager)..."))

        val driver: ChromeDriver = try {
            ChromeDriverFactory.create()
        } catch (e: Exception) {
            send(ScraperProgress.Error("Не удалось запустить Chrome: ${e.message}"))
            return@channelFlow
        }

        send(ScraperProgress.InProgress("Chrome started successfully"))

        val savedFiles = mutableListOf<String>()

        try {
            for (pageNum in pagesToScrape) {
                var success = false
                var lastError: Exception? = null

                // Логика повторных попыток (без изменений)
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val result = scrapePage(driver, baseUrl, pageNum, saveDir)
                        if (result != null) {
                            savedFiles.add(result)
                            send(ScraperProgress.InProgress("Успешно сохранено: ${File(result).name}"))
                            success = true
                            break
                        }
                    } catch (e: Exception) {
                        lastError = e
                        send(ScraperProgress.InProgress("Попытка $attempt/$MAX_RETRIES не удалась: ${e.message}"))
                        if (attempt < MAX_RETRIES) {
                            val delayMs = INITIAL_RETRY_DELAY_MS * attempt
                            delay(delayMs)
                        }
                    }
                }

                if (!success) {
                    send(ScraperProgress.InProgress("Ошибка: не удалось сохранить страницу $pageNum"))
                    lastError?.printStackTrace()
                }

                val sleepTime = Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS)
                delay(sleepTime)
            }

            send(ScraperProgress.InProgress("Download complete. Files: ${savedFiles.size}"))
            send(ScraperProgress.Success(savedFiles))
        } catch (e: Exception) {
            send(ScraperProgress.Error("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}"))
            e.printStackTrace()
        } finally {
            send(ScraperProgress.InProgress("Закрываю браузер."))
            try {
                ChromeDriverFactory.quit(driver)
            } catch (e: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun scrapePage(
        driver: ChromeDriver,
        baseUrl: String,
        pageNum: Int,
        saveDir: File
    ): String? = withContext(Dispatchers.IO) {
        val startOffset = (pageNum - 1) * 20
        val pageUrl = "$baseUrl&st=$startOffset"
        val targetFile = File(saveDir, "page_${pageNum}.html")

        println("Открываю страницу $pageNum: $pageUrl")
        driver.get(pageUrl)

        val wait = WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS))

        // Wait for content to load
        try {
            wait.until { driver.findElement(By.className("block-title")).isDisplayed }
        } catch (e: WebDriverException) {
            // Try alternative selector
            wait.until { driver.findElement(By.className("postcolor")).isDisplayed }
        }

        println("Контент загружен.")
        val htmlContent = driver.pageSource
        targetFile.writeText(htmlContent.orEmpty(), StandardCharsets.UTF_8)

        if (targetFile.exists() && targetFile.length() > 0) {
            println("Сохранено: ${targetFile.name} (${targetFile.length() / 1024} KB)")
            targetFile.absolutePath
        } else {
            println("ERROR: File not created or empty")
            null
        }
    }

    /**
     * Parse index from the first page (page_1.html) to extract links from spoilers.
     * Returns list of IndexLink with postId, title, category from the first page.
     */
    override fun parseIndexFromFirstPage(): List<IndexLink> {
        val firstPageFile = File(getSaveDirectory(), "page_1.html")
        if (!firstPageFile.exists()) {
            println("[DesktopWebScraper] First page not found: ${firstPageFile.absolutePath}")
            return emptyList()
        }

        return try {
            val htmlContent = firstPageFile.readText(StandardCharsets.UTF_8)
            val parser = IndexParser()
            val topicUrl = "https://4pda.to/forum/index.php?showtopic=1109539"

            // Parse using the synchronous parseIndex method
            val result = runBlocking {
                parser.parseIndex(htmlContent, topicUrl)
            }

            when (result) {
                is IndexParseResult.Success -> {
                    println("[DesktopWebScraper] Parsed ${result.index.links.size} links from index")
                    result.index.links
                }

                is IndexParseResult.Error -> {
                    println("[DesktopWebScraper] Error parsing index: ${result.message}")
                    emptyList()
                }

                is IndexParseResult.Cached -> {
                    println("[DesktopWebScraper] Using cached index with ${result.index.links.size} links")
                    result.index.links
                }
            }
        } catch (e: Exception) {
            println("[DesktopWebScraper] Error reading first page: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get original prompt content from saved HTML file by postId.
     * Returns HTML content of the post or null if not found.
     */
    override fun getPromptContentFromHtml(postId: String): String? {
        val saveDir = File(getSaveDirectory())

        // Search through all HTML files for the post
        val htmlFiles = saveDir.listFiles { f ->
            f.isFile && f.name.startsWith("page_") && f.name.endsWith(".html")
        } ?: return null

        for (htmlFile in htmlFiles) {
            try {
                val htmlContent = htmlFile.readText(StandardCharsets.UTF_8)
                val document = Jsoup.parse(htmlContent)

                // Find the post by data-post attribute
                val postElement = document.selectFirst("div.post[data-post='$postId']")
                if (postElement != null) {
                    val contentElement = postElement.selectFirst("div.postcolor")
                    if (contentElement != null) {
                        return contentElement.html()
                    }
                }
            } catch (e: Exception) {
                // Continue to next file
            }
        }

        return null
    }
}