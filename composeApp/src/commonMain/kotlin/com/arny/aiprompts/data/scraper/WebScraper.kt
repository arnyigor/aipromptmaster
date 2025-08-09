package com.arny.aiprompts.data.scraper

import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.Desktop
import java.time.Duration
import kotlin.random.Random

import java.io.File
import java.nio.charset.StandardCharsets

interface WebScraper {
    fun getSaveDirectory(): File
    fun openSaveDirectory()
    // Метод проверки теперь возвращает новую структуру
    fun checkExistingFiles(totalPages: Int): PreScrapeCheck
    fun getExistingScrapedFiles(): List<File>
    // Метод скрапинга теперь принимает список страниц для скачивания
    fun scrapeAndSave(
        baseUrl: String,
        pagesToScrape: List<Int>, // Например, [0, 1, 4, 5]
        onProgress: (String) -> Unit
    ): List<File>
}

// Обновляем PreScrapeCheck, чтобы он хранил список недостающих страниц
data class PreScrapeCheck(
    val existingFileCount: Int,
    val missingPages: List<Int> // Список номеров страниц (с 0)
)

class SeleniumWebScraper : WebScraper {

    // Выносим директорию в отдельный метод для легкого доступа
    override fun getSaveDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".aiprompts/scraped_html")
        dir.mkdirs()
        return dir
    }

    override fun openSaveDirectory() {
        val dir = getSaveDirectory()
        // Проверяем, поддерживается ли действие "OPEN" на текущей платформе
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
        } else {
            // Можно добавить фоллбэк или просто ничего не делать,
            // но на основных десктопных ОС это всегда будет работать.
            println("Действие 'Открыть директорию' не поддерживается на этой системе.")
        }
    }

    override fun getExistingScrapedFiles(): List<File> {
        return getSaveDirectory()
            .listFiles { file -> file.isFile && file.name.startsWith("page_") && file.name.endsWith(".html") }
            ?.sortedBy { file ->
                // Сортируем по номеру страницы для правильного порядка
                file.name.substringAfter("_").substringBefore(".").toIntOrNull() ?: 0
            }
            ?: emptyList()
    }


    // --- НОВЫЙ, УМНЫЙ МЕТОД ПРОВЕРКИ ---
    override fun checkExistingFiles(totalPages: Int): PreScrapeCheck {
        val saveDir = getSaveDirectory()
        val missing = mutableListOf<Int>()
        var existingCount = 0

        for (pageNum in 0 until totalPages) {
            if (File(saveDir, "page_${pageNum + 1}.html").exists()) {
                existingCount++
            } else {
                missing.add(pageNum)
            }
        }
        return PreScrapeCheck(existingCount, missing)
    }

    override fun scrapeAndSave(
        baseUrl: String,
        pagesToScrape: List<Int>,
        onProgress: (String) -> Unit
    ): List<File> {
        if (pagesToScrape.isEmpty()) {
            onProgress("Нет страниц для скачивания.")
            return emptyList()
        }
        onProgress("Запускаю скачивание для ${pagesToScrape.size} страниц: ${pagesToScrape.map { it + 1 }}")

        val saveDir = getSaveDirectory()
        onProgress("Сохранение в директорию: ${saveDir.absolutePath}")

        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
        }

        val driver = ChromeDriver(options)
        val savedFiles = mutableListOf<File>()

        try {
            // Итерируемся по списку нужных нам страниц
            for (pageNum in pagesToScrape) {
                val startOffset = pageNum * 20
                val pageUrl = "$baseUrl&st=$startOffset"
                val targetFile = File(saveDir, "page_${pageNum + 1}.html")

                onProgress("Открываю страницу ${pageNum + 1}: $pageUrl")
                driver.get(pageUrl)

                val wait = WebDriverWait(driver, Duration.ofSeconds(15))
                wait.until { driver.findElement(By.className("block-title")).isDisplayed }
                onProgress("Контент загружен.")

                val htmlContent = driver.pageSource
                targetFile.writeText(htmlContent, StandardCharsets.UTF_8)

                if (targetFile.exists() && targetFile.length() > 0) {
                    onProgress("Успешно сохранено: ${targetFile.name} (${targetFile.length() / 1024} KB)")
                    savedFiles.add(targetFile)
                } else {
                    onProgress("Ошибка: не удалось сохранить файл ${targetFile.name}")
                }

                val sleepTime = Random.nextLong(1500, 3000)
                onProgress("Пауза на ${sleepTime / 1000.0} секунд...")
                Thread.sleep(sleepTime)
            }
        } catch (e: Exception) {
            onProgress("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
            e.printStackTrace()
        } finally {
            onProgress("Закрываю браузер.")
            driver.quit()
        }
        return savedFiles
    }
}