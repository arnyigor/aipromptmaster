package com.arny.aiprompts.data.scraper

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions

object ChromeDriverFactory {
    private val options: ChromeOptions by lazy { buildOptions() }

    /** Создаёт новый драйвер (или отдаёт из пула). */
    fun create(): ChromeDriver =
        try {
            ChromeDriver(options)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("ChromeDriver init failed:${e.stackTraceToString()}", )
        }

    /** Гибко закрывает драйверы. */
    fun quit(driver: ChromeDriver?) = driver?.let { try { it.quit() } catch (_: Exception) {} }

    /** Встроенная сборка опций (можно переопределять). */
    private fun buildOptions(): ChromeOptions {
        return ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--remote-allow-origins=*")
            addArguments("--disable-blink-features=AutomationControlled")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
        }
    }
}
