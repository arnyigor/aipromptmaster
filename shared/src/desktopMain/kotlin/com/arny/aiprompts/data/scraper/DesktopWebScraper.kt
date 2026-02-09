package com.arny.aiprompts.data.scraper

import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.interfaces.PreScrapeCheck
import com.arny.aiprompts.domain.interfaces.ScraperProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
 */
class DesktopWebScraper : IWebScraper {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val PAGE_LOAD_TIMEOUT_SECONDS = 15L
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

        send(ScraperProgress.InProgress(
            "Запускаю скачивание для ${pagesToScrape.size} страниц: ${pagesToScrape.map { it }}"
        ))

        val saveDir = File(getSaveDirectory())
        send(ScraperProgress.InProgress("Сохранение в директорию: ${saveDir.absolutePath}"))

        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }

        val driver = ChromeDriver(options)
        val savedFiles = mutableListOf<String>()

        try {
            for (pageNum in pagesToScrape) {
                var success = false
                var lastError: Exception? = null

                // Retry logic with exponential backoff
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        val result = scrapePage(driver, baseUrl, pageNum, saveDir)
                        if (result != null) {
                            savedFiles.add(result)
                            send(ScraperProgress.InProgress(
                                "Успешно сохранено: ${File(result).name}"
                            ))
                            success = true
                            break
                        }
                    } catch (e: Exception) {
                        lastError = e
                        send(ScraperProgress.InProgress(
                            "Попытка $attempt/$MAX_RETRIES не удалась для страницы $pageNum: ${e.message}"
                        ))
                        if (attempt < MAX_RETRIES) {
                            val delayMs = INITIAL_RETRY_DELAY_MS * attempt
                            send(ScraperProgress.InProgress("Повторная попытка через ${delayMs}ms..."))
                            delay(delayMs)
                        }
                    }
                }

                if (!success) {
                    send(ScraperProgress.InProgress(
                        "Ошибка: не удалось сохранить страницу $pageNum после $MAX_RETRIES попыток"
                    ))
                    lastError?.printStackTrace()
                }

                // Random delay between requests (non-blocking)
                val sleepTime = Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS)
                send(ScraperProgress.InProgress("Пауза на ${sleepTime / 1000.0} секунд..."))
                delay(sleepTime)
            }

            send(ScraperProgress.Success(savedFiles))
        } catch (e: Exception) {
            send(ScraperProgress.Error("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}"))
            e.printStackTrace()
        } finally {
            send(ScraperProgress.InProgress("Закрываю браузер."))
            try {
                driver.quit()
            } catch (e: Exception) {
                // Ignore cleanup errors
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
        targetFile.writeText(htmlContent, StandardCharsets.UTF_8)

        if (targetFile.exists() && targetFile.length() > 0) {
            println("Сохранено: ${targetFile.name} (${targetFile.length() / 1024} KB)")
            targetFile.absolutePath
        } else {
            null
        }
    }
}