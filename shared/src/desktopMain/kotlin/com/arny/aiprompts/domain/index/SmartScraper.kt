package com.arny.aiprompts.domain.index

import com.arny.aiprompts.domain.index.model.IndexLink
import com.arny.aiprompts.domain.index.model.ParsedIndex
import com.arny.aiprompts.domain.index.model.PostLocation
import com.arny.aiprompts.domain.interfaces.IWebScraper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * Smart scraper that downloads only required pages based on index links.
 * Uses redirect resolution (Variant A) to find exact page locations.
 */
class SmartScraper(
    private val webScraper: IWebScraper,
    private val cacheManager: IndexCacheManager
) {

    companion object {
        private const val REDIRECT_DELAY_MS = 2000L
        private const val PAGE_DOWNLOAD_DELAY_MS = 1500L
        private const val MAX_REDIRECT_RETRIES = 3
    }

    /**
     * Progress events during scraping.
     */
    sealed class ScrapingProgress {
        data class ResolvingLinks(val total: Int, val current: Int, val postId: String) : ScrapingProgress()
        data class ResolvingComplete(val locations: List<PostLocation>) : ScrapingProgress()
        data class DownloadingPage(val pageOffset: Int, val postsOnPage: Int) : ScrapingProgress()
        data class ExtractingPosts(val pageOffset: Int, val postsFound: Int) : ScrapingProgress()
        data class PostExtracted(val postId: String, val success: Boolean, val error: String? = null) : ScrapingProgress()
        data class Complete(val extractedPosts: Int, val failedPosts: List<String>) : ScrapingProgress()
        data class Error(val message: String) : ScrapingProgress()
    }

    /**
     * Step 1: Resolve all index links to find their page locations using redirects.
     * Uses Selenium to follow redirects and capture final URL.
     */
    suspend fun resolvePageLocations(
        index: ParsedIndex,
        onProgress: suspend (ScrapingProgress) -> Unit = {}
    ): List<PostLocation> = withContext(Dispatchers.IO) {
        
        val locations = mutableListOf<PostLocation>()
        val totalLinks = index.links.size
        
        println("[SmartScraper] Resolving ${totalLinks} links to page locations...")
        
        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
        }
        
        val driver = ChromeDriver(options)
        
        try {
            index.links.forEachIndexed { idx, link ->
                onProgress(ScrapingProgress.ResolvingLinks(totalLinks, idx + 1, link.postId))
                
                var retries = 0
                var location: PostLocation? = null
                
                while (retries < MAX_REDIRECT_RETRIES && location == null) {
                    try {
                        // Navigate to findpost URL
                        val findpostUrl = buildFindpostUrl(index.topicId, link.postId)
                        println("[SmartScraper] Navigating: $findpostUrl")
                        
                        driver.get(findpostUrl)
                        delay(REDIRECT_DELAY_MS)
                        
                        // Get final URL after redirect
                        val finalUrl = driver.currentUrl
                        println("[SmartScraper] Final URL: $finalUrl")
                        
                        // Extract page offset from final URL
                        val pageOffset = extractPageOffset(finalUrl)
                        
                        if (pageOffset != null) {
                            location = PostLocation(
                                postId = link.postId,
                                pageOffset = pageOffset,
                                directUrl = finalUrl
                            )
                            locations.add(location)
                            println("[SmartScraper] Resolved: postId=${link.postId} -> st=$pageOffset")
                        } else {
                            // If no st parameter, probably first page
                            location = PostLocation(
                                postId = link.postId,
                                pageOffset = 0,
                                directUrl = finalUrl
                            )
                            locations.add(location)
                            println("[SmartScraper] Resolved (first page): postId=${link.postId}")
                        }
                        
                    } catch (e: Exception) {
                        retries++
                        println("[SmartScraper] Retry $retries for postId=${link.postId}: ${e.message}")
                        delay(1000L * retries)
                    }
                }
                
                if (location == null) {
                    println("[SmartScraper] Failed to resolve postId=${link.postId} after $MAX_REDIRECT_RETRIES retries")
                }
                
                // Random delay between requests
                delay(Random.nextLong(500, 1500))
            }
        } finally {
            driver.quit()
        }
        
        onProgress(ScrapingProgress.ResolvingComplete(locations))
        println("[SmartScraper] Resolved ${locations.size}/${totalLinks} links")
        
        locations
    }

    /**
     * Step 2: Download pages and extract specific posts.
     */
    suspend fun downloadAndExtractPosts(
        index: ParsedIndex,
        locations: List<PostLocation>,
        onProgress: suspend (ScrapingProgress) -> Unit = {}
    ): List<ExtractedPost> = withContext(Dispatchers.IO) {
        
        val extractedPosts = mutableListOf<ExtractedPost>()
        val failedPosts = mutableListOf<String>()
        
        // Group locations by page offset
        val byPage = locations.groupBy { it.pageOffset }
        println("[SmartScraper] Need to download ${byPage.size} unique pages")
        
        byPage.forEach { (pageOffset, pageLocations) ->
            onProgress(ScrapingProgress.DownloadingPage(pageOffset, pageLocations.size))
            
            try {
                // Check if page already cached
                val cachedHtml = loadCachedPage(pageOffset)
                
                val htmlContent = if (cachedHtml != null) {
                    println("[SmartScraper] Using cached page st=$pageOffset")
                    cachedHtml
                } else {
                    // Download page
                    val pageUrl = buildPageUrl(index.topicId, pageOffset)
                    println("[SmartScraper] Downloading: $pageUrl")
                    
                    val downloaded = downloadPage(pageUrl)
                    if (downloaded != null) {
                        savePageToCache(pageOffset, downloaded)
                        downloaded
                    } else {
                        failedPosts.addAll(pageLocations.map { it.postId })
                        return@forEach
                    }
                }
                
                // Extract specific posts from this page
                onProgress(ScrapingProgress.ExtractingPosts(pageOffset, pageLocations.size))
                
                val posts = extractPostsFromPage(htmlContent, pageLocations.map { it.postId })
                
                posts.forEach { post ->
                    val success = post.textContent.isNotBlank()
                    onProgress(ScrapingProgress.PostExtracted(post.postId, success, 
                        if (!success) "Empty content" else null))
                    
                    if (success) {
                        extractedPosts.add(post)
                    } else {
                        failedPosts.add(post.postId)
                    }
                }
                
            } catch (e: Exception) {
                println("[SmartScraper] Error processing page st=$pageOffset: ${e.message}")
                failedPosts.addAll(pageLocations.map { it.postId })
            }
            
            delay(PAGE_DOWNLOAD_DELAY_MS)
        }
        
        onProgress(ScrapingProgress.Complete(extractedPosts.size, failedPosts))
        extractedPosts
    }

    /**
     * Full pipeline: resolve -> download -> extract.
     */
    fun scrapeFromIndex(index: ParsedIndex): Flow<ScrapingProgress> = flow {
        // Step 1: Resolve page locations
        val locations = resolvePageLocations(index) { emit(it) }
        
        if (locations.isEmpty()) {
            emit(ScrapingProgress.Error("No page locations resolved"))
            return@flow
        }
        
        // Update index with locations
        val updatedIndex = IndexParser().updateWithPageLocations(index, locations)
        cacheManager.saveCache(updatedIndex)
        
        // Step 2: Download and extract
        downloadAndExtractPosts(updatedIndex, locations) { emit(it) }
        
    }.flowOn(Dispatchers.IO)

    /**
     * Build findpost URL for resolving redirects.
     */
    private fun buildFindpostUrl(topicId: String, postId: String): String {
        return "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$postId"
    }

    /**
     * Build page URL for downloading.
     */
    private fun buildPageUrl(topicId: String, pageOffset: Int): String {
        return if (pageOffset == 0) {
            "https://4pda.to/forum/index.php?showtopic=$topicId"
        } else {
            "https://4pda.to/forum/index.php?showtopic=$topicId&st=$pageOffset"
        }
    }

    /**
     * Extract page offset (st parameter) from URL.
     */
    private fun extractPageOffset(url: String): Int? {
        val regex = Regex("""[?&]st=(\d+)""")
        val match = regex.find(url)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Load cached page HTML if exists.
     */
    private fun loadCachedPage(pageOffset: Int): String? {
        val cacheDir = File(webScraper.getSaveDirectory())
        val pageNum = (pageOffset / 20) + 1
        val cacheFile = File(cacheDir, "page_$pageNum.html")
        
        return if (cacheFile.exists()) {
            cacheFile.readText(StandardCharsets.UTF_8)
        } else null
    }

    /**
     * Save downloaded page to cache.
     */
    private fun savePageToCache(pageOffset: Int, html: String) {
        val cacheDir = File(webScraper.getSaveDirectory())
        val pageNum = (pageOffset / 20) + 1
        val cacheFile = File(cacheDir, "page_$pageNum.html")
        cacheFile.writeText(html, StandardCharsets.UTF_8)
        println("[SmartScraper] Cached page st=$pageOffset to ${cacheFile.name}")
    }

    /**
     * Download page using simple HTTP or Selenium.
     */
    private suspend fun downloadPage(url: String): String? = withContext(Dispatchers.IO) {
        // For now use Selenium for consistency
        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
        }
        
        val driver = ChromeDriver(options)
        try {
            driver.get(url)
            delay(3000) // Wait for content
            driver.pageSource
        } catch (e: Exception) {
            println("[SmartScraper] Download failed: ${e.message}")
            null
        } finally {
            driver.quit()
        }
    }

    /**
     * Extract specific posts from page HTML by post IDs.
     */
    private fun extractPostsFromPage(html: String, postIds: List<String>): List<ExtractedPost> {
        val document = org.jsoup.Jsoup.parse(html)
        val posts = mutableListOf<ExtractedPost>()
        
        postIds.forEach { postId ->
            val postElement = document.selectFirst("div.post[data-post='$postId']")
            
            if (postElement != null) {
                val authorName = postElement.selectFirst("a[href*='showuser']")?.text() ?: "Unknown"
                val authorId = postElement.selectFirst("a[href*='showuser']")?.attr("href")
                    ?.let { Regex("""showuser=(\d+)""").find(it)?.groupValues?.get(1) } ?: ""
                
                val contentElement = postElement.selectFirst("div.postcolor")
                val content = contentElement?.html() ?: ""
                val textContent = contentElement?.text() ?: ""
                
                posts.add(ExtractedPost(
                    postId = postId,
                    authorName = authorName,
                    authorId = authorId,
                    htmlContent = content,
                    textContent = textContent,
                    url = "https://4pda.to/forum/index.php?showtopic=1109539#entry$postId"
                ))
            } else {
                posts.add(ExtractedPost(
                    postId = postId,
                    authorName = "",
                    authorId = "",
                    htmlContent = "",
                    textContent = "",
                    url = "",
                    error = "Post not found on page"
                ))
            }
        }
        
        return posts
    }

    /**
     * Extracted post data.
     */
    data class ExtractedPost(
        val postId: String,
        val authorName: String,
        val authorId: String,
        val htmlContent: String,
        val textContent: String,
        val url: String,
        val error: String? = null
    )
}