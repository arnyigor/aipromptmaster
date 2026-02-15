package com.arny.aiprompts.data.scraper

import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.interfaces.PreScrapeCheck
import com.arny.aiprompts.domain.interfaces.ScraperProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub implementation of IWebScraper for Android.
 * Required to satisfy DI graph since ScrapeWebsiteUseCase is in commonMain.
 * Scraping is not supported on mobile.
 */
class NoOpWebScraper : IWebScraper {
    override fun getSaveDirectory(): String = ""

    override fun openSaveDirectory() {
        // Not supported on Android
    }

    override fun checkExistingFiles(pages: List<Int>): PreScrapeCheck {
        return PreScrapeCheck(0, pages)
    }

    override fun getExistingScrapedFiles(): List<String> {
        return emptyList()
    }

    override fun scrapeAndSave(baseUrl: String, pagesToScrape: List<Int>): Flow<ScraperProgress> {
        return flow {
            emit(ScraperProgress.Error("Скрапинг не поддерживается в мобильной версии."))
        }
    }
}
