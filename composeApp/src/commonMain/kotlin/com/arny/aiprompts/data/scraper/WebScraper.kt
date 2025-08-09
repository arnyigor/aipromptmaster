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
    // Новый метод для предварительной проверки
    fun checkExistingFiles(pages: Int): PreScrapeCheck
    fun getExistingScrapedFiles(): List<File>
    // Метод скрапинга теперь принимает начальную страницу
    fun scrapeAndSave(
        baseUrl: String,
        pages: Int,
        startPage: Int, // Начинаем с этой страницы (индекс с 0)
        onProgress: (String) -> Unit
    ): List<File>
}

// Создадим небольшой data class для передачи результата проверки
data class PreScrapeCheck(
    val existingFileCount: Int,
    val canContinue: Boolean // True, если есть что продолжать
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


    // --- НОВЫЙ МЕТОД ПРОВЕРКИ ---
    override fun checkExistingFiles(pages: Int): PreScrapeCheck {
        val saveDir = getSaveDirectory()
        var lastFoundPage = -1
        for (i in 0 until pages) {
            if (File(saveDir, "page_${i + 1}.html").exists()) {
                lastFoundPage = i
            } else {
                // Прерываемся, как только нашли "дырку" в последовательности
                break
            }
        }
        val existingCount = lastFoundPage + 1
        return PreScrapeCheck(
            existingFileCount = existingCount,
            canContinue = existingCount > 0 && existingCount < pages
        )
    }

    override fun scrapeAndSave(
        baseUrl: String,
        pages: Int,
        startPage: Int,
        onProgress: (String) -> Unit
    ): List<File> {
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
            // Начинаем цикл с правильной страницы
            for (pageNum in startPage until pages) {
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