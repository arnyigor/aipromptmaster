package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.model.ScrapedPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow // <-- ИМПОРТИРУЕМ channelFlow
import kotlinx.coroutines.withContext

class ScrapeWebsiteUseCase(private val webScraper: WebScraper) {
    /**
     * Запускает скрапинг и эмитит прогресс и результат через Flow.
     * Использует channelFlow для безопасной эмиссии данных из не-suspend коллбэка.
     */
    operator fun invoke(baseUrl: String, pages: Int): Flow<ScraperResult> = channelFlow {
        // Начальное сообщение
        send(ScraperResult.InProgress("Начинаю процесс..."))

        try {
            // Запускаем блокирующую операцию в отдельном потоке
            val posts = withContext(Dispatchers.IO) {
                // Вся тяжелая работа здесь
                webScraper.scrapeUrl(baseUrl, pages) { progressMessage ->
                    // Внутри коллбэка onProgress...
                    // Используем trySend, чтобы не блокировать поток скрапера,
                    // если потребитель (UI) не успевает обрабатывать сообщения.
                    trySend(ScraperResult.InProgress(progressMessage))
                }
            }
            // Отправляем финальный успешный результат
            send(ScraperResult.Success(posts))
        } catch (e: Exception) {
            e.printStackTrace()
            // Отправляем ошибку
            send(ScraperResult.Error(e.message ?: "Неизвестная ошибка скрапинга"))
        }
    }
}

// Sealed-класс для представления состояний скрапинга (остается без изменений)
sealed interface ScraperResult {
    data class InProgress(val message: String) : ScraperResult
    data class Success(val posts: List<ScrapedPost>) : ScraperResult
    data class Error(val errorMessage: String) : ScraperResult
}