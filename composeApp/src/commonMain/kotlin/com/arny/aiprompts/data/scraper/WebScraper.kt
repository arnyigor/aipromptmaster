package com.arny.aiprompts.data.scraper

import com.arny.aiprompts.domain.model.ScrapedPost
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import kotlin.random.Random

// Интерфейс для тестируемости
interface WebScraper {
      fun scrapeUrl(
        baseUrl: String,
        pages: Int,
        onProgress: (String) -> Unit // Лямбда для сообщений о прогрессе
    ): List<ScrapedPost>
}

class SeleniumWebScraper : WebScraper {

    override fun scrapeUrl(
        baseUrl: String,
        pages: Int,
        onProgress: (String) -> Unit
    ): List<ScrapedPost> {
        onProgress("Запускаю скрапинг для URL: $baseUrl")
        onProgress("Количество страниц для обработки: $pages")

        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
        }

        // Selenium Manager сам найдет и скачает нужный chromedriver
        val driver = ChromeDriver(options)
        val allPosts = mutableListOf<ScrapedPost>()

        try {
            for (pageNum in 0 until pages) {
                val startOffset = pageNum * 20
                val pageUrl = "$baseUrl&st=$startOffset"

                onProgress("Открываю страницу: $pageUrl")
                driver.get(pageUrl)

                // Ожидание загрузки контента
                val wait = WebDriverWait(driver, Duration.ofSeconds(15))
                wait.until {
                    driver.findElement(By.className("block-title")).isDisplayed
                }
                onProgress("Контент загружен. Парсинг...")

                val postsOnPage = parsePostsFromHtml(driver.pageSource)
                if (postsOnPage.isNotEmpty()) {
                    onProgress("На странице ${pageNum + 1} найдено ${postsOnPage.size} постов.")
                    allPosts.addAll(postsOnPage)
                } else {
                    onProgress("На странице ${pageNum + 1} посты не найдены.")
                }

                val sleepTime = Random.nextLong(2500, 5000)
                onProgress("Пауза на ${sleepTime / 1000.0} секунд...")
                // ЗАМЕНЯЕМ delay НА Thread.sleep
                Thread.sleep(sleepTime)
            }
        } catch (e: Exception) {
            onProgress("ОШИБКА: ${e.message}")
            e.printStackTrace()
        } finally {
            onProgress("Закрываю браузер.")
            driver.quit()
        }
        return allPosts
    }

    private fun parsePostsFromHtml(html: String): List<ScrapedPost> {
        val document: Document = Jsoup.parse(html)
        return document.select("table[data-post]").mapNotNull { container ->
            val author = container.selectFirst("span.normalname")?.text()?.trim()
            val text = container.selectFirst("div.postcolor")?.text()?.trim()
            val postId = container.attr("data-post")

            if (author != null && text != null) {
                ScrapedPost(author, text, postId)
            } else {
                null
            }
        }
    }
}