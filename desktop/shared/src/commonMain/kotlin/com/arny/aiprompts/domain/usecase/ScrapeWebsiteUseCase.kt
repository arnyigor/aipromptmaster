package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.interfaces.ScraperProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase for scraping website pages using IWebScraper.
 * Wraps platform-specific scraper implementation.
 */
class ScrapeWebsiteUseCase(private val webScraper: IWebScraper) {

    operator fun invoke(baseUrl: String, pagesToScrape: List<Int>): Flow<ScraperResult> {
        return webScraper.scrapeAndSave(baseUrl, pagesToScrape)
            .map { progress ->
                when (progress) {
                    is ScraperProgress.InProgress -> ScraperResult.InProgress(progress.message)
                    is ScraperProgress.Success -> ScraperResult.Success(progress.files)
                    is ScraperProgress.Error -> ScraperResult.Error(progress.errorMessage)
                }
            }
    }
}

sealed interface ScraperResult {
    data class InProgress(val message: String) : ScraperResult
    data class Success(val files: List<String>) : ScraperResult
    data class Error(val errorMessage: String) : ScraperResult
}