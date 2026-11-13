package com.arny.aiprompts.domain.index

import com.arny.aiprompts.domain.index.model.ParsedIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages caching of parsed index data with TTL support.
 * Cache location: ~/.aiprompts/index_cache/
 */
class IndexCacheManager {

    companion object {
        private const val CACHE_DIR_NAME = "index_cache"
        private const val INDEX_CACHE_FILE = "parsed_index.json"
        private const val DEFAULT_TTL_HOURS = 24
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Get the cache directory path.
     */
    fun getCacheDirectory(): String {
        val dir = File(System.getProperty("user.home"), ".aiprompts/$CACHE_DIR_NAME")
        dir.mkdirs()
        return dir.absolutePath
    }

    /**
     * Check if valid cached index exists.
     */
    suspend fun hasValidCache(topicId: String, ttlHours: Int = DEFAULT_TTL_HOURS): Boolean = 
        withContext(Dispatchers.IO) {
            val cached = loadCache(topicId)
            cached?.isValid(ttlHours) ?: false
        }

    /**
     * Load cached index if it exists and is valid.
     */
    suspend fun loadCache(topicId: String): ParsedIndex? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(getCacheDirectory(), "${topicId}_$INDEX_CACHE_FILE")
            if (!cacheFile.exists()) return@withContext null

            val content = cacheFile.readText()
            json.decodeFromString<ParsedIndex>(content)
        } catch (e: Exception) {
            println("[IndexCacheManager] Error loading cache: ${e.message}")
            null
        }
    }

    /**
     * Save parsed index to cache.
     */
    suspend fun saveCache(index: ParsedIndex) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(getCacheDirectory(), "${index.topicId}_$INDEX_CACHE_FILE")
            val jsonContent = json.encodeToString(index)
            cacheFile.writeText(jsonContent)
            println("[IndexCacheManager] Cache saved: ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            println("[IndexCacheManager] Error saving cache: ${e.message}")
        }
    }

    /**
     * Clear cache for specific topic.
     */
    suspend fun clearCache(topicId: String) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(getCacheDirectory(), "${topicId}_$INDEX_CACHE_FILE")
            if (cacheFile.exists()) {
                cacheFile.delete()
                println("[IndexCacheManager] Cache cleared for topic: $topicId")
            }
        } catch (e: Exception) {
            println("[IndexCacheManager] Error clearing cache: ${e.message}")
        }
    }

    /**
     * Clear all index caches.
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(getCacheDirectory())
            cacheDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { it.delete() }
            println("[IndexCacheManager] All cache cleared")
        } catch (e: Exception) {
            println("[IndexCacheManager] Error clearing all cache: ${e.message}")
        }
    }

    /**
     * Get cache file info for debugging.
     */
    suspend fun getCacheInfo(topicId: String): CacheInfo? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(getCacheDirectory(), "${topicId}_$INDEX_CACHE_FILE")
            if (!cacheFile.exists()) return@withContext null

            val age = System.currentTimeMillis() - cacheFile.lastModified()
            val ageHours = age / (1000 * 60 * 60)

            CacheInfo(
                filePath = cacheFile.absolutePath,
                fileSize = cacheFile.length(),
                ageMillis = age,
                ageHours = ageHours,
                isValid = ageHours < DEFAULT_TTL_HOURS
            )
        } catch (e: Exception) {
            null
        }
    }

    data class CacheInfo(
        val filePath: String,
        val fileSize: Long,
        val ageMillis: Long,
        val ageHours: Long,
        val isValid: Boolean
    )
}