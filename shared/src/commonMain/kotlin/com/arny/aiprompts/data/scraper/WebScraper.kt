package com.arny.aiprompts.data.scraper

import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.random.Random

interface WebScraper {
    fun getSaveDirectory(): File
    fun openSaveDirectory()

    // Метод проверки теперь возвращает новую структуру
    fun checkExistingFiles(pages: List<Int>): PreScrapeCheck
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


    /**
     * Проверяет наличие файлов для указанного списка номеров страниц.
     * @param pages Список номеров страниц для проверки (например, [1, 2, 3, 5, 8]).
     * @return PreScrapeCheck с количеством существующих файлов и списком отсутствующих номеров страниц.
     */
    override fun checkExistingFiles(pages: List<Int>): PreScrapeCheck {
        val saveDir = getSaveDirectory()
        val missingPages = mutableListOf<Int>()
        var existingFileCount = 0

        // Основное изменение: итерируемся по конкретному списку страниц, а не по диапазону.
        for (pageNumber in pages) {
            // Мы работаем напрямую с номером страницы, что делает код чище.
            // Убедитесь, что ваш парсер возвращает страницы с 1, а не с 0.
            // Наш PageStringParser из предыдущего ответа делает именно так.
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
        pagesToScrape: List<Int>,
        onProgress: (String) -> Unit
    ): List<File> {
        if (pagesToScrape.isEmpty()) {
            onProgress("Нет страниц для скачивания.")
            return emptyList()
        }
        onProgress("Запускаю скачивание для ${pagesToScrape.size} страниц: ${pagesToScrape.map { it }}")

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
                val startOffset = (pageNum - 1) * 20
                val pageUrl = "$baseUrl&st=$startOffset"
                val targetFile = File(saveDir, "page_${pageNum}.html")

                onProgress("Открываю страницу ${pageNum}: $pageUrl")
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