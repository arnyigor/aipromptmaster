package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.scraper.WebScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File

class ScrapeWebsiteUseCase(private val webScraper: WebScraper) {
    operator fun invoke(baseUrl: String, pagesToScrape: List<Int>): Flow<ScraperResult> = channelFlow {
        send(ScraperResult.InProgress("Начинаю процесс..."))
        try {
            val files = withContext(Dispatchers.IO) {
                webScraper.scrapeAndSave(baseUrl, pagesToScrape) { progressMessage ->
                    trySend(ScraperResult.InProgress(progressMessage))
                }
            }
            send(ScraperResult.Success(files))
        } catch (e: Exception) {
            // ...
        }
    }
}

// Обновляем ScraperResult, чтобы он работал с File
sealed interface ScraperResult {
    data class InProgress(val message: String) : ScraperResult
    data class Success(val files: List<File>) : ScraperResult
    data class Error(val errorMessage: String) : ScraperResult
}