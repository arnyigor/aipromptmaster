package com.arny.aiprompts.domain.interfaces

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic interface for web scraping.
 * Implementation is provided in platform-specific modules (desktopMain).
 */
interface IWebScraper {
    /**
     * Returns the directory where scraped files are saved.
     */
    fun getSaveDirectory(): String

    /**
     * Opens the save directory in file explorer (desktop only).
     */
    fun openSaveDirectory()

    /**
     * Checks which pages are already downloaded.
     */
    fun checkExistingFiles(pages: List<Int>): PreScrapeCheck

    /**
     * Returns list of already scraped HTML files.
     */
    fun getExistingScrapedFiles(): List<String>

    /**
     * Scrapes pages and saves HTML files.
     * Returns flow of progress updates.
     */
    fun scrapeAndSave(
        baseUrl: String,
        pagesToScrape: List<Int>
    ): Flow<ScraperProgress>
}

data class PreScrapeCheck(
    val existingFileCount: Int,
    val missingPages: List<Int>
)

sealed class ScraperProgress {
    data class InProgress(val message: String) : ScraperProgress()
    data class Success(val files: List<String>) : ScraperProgress()
    data class Error(val errorMessage: String) : ScraperProgress()
}