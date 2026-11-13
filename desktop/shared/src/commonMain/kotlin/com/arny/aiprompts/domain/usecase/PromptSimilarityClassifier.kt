package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.utils.TextUtils
import com.arny.aiprompts.domain.model.PromptMatchResult
import kotlin.math.max

class PromptSimilarityClassifier(private val prompts: List<String>) {

    private data class TfidfCandidate(val index: Int, val cosineScore: Double)

    private val documentTokens: List<List<String>> = prompts.map { TextUtils.tokenize(it) }
    private val idf: Map<String, Double>
    private val dbTfidfVectors: List<Map<String, Double>>

    init {
        this.idf = calculateIdf()
        this.dbTfidfVectors = this.documentTokens.map { calculateTfidf(it) }
    }

    fun findSimilar(
        queryPrompt: String,
        tfidfTopN: Int = 10,
        levenshteinThreshold: Double = 0.9
    ): List<PromptMatchResult> {
        if (queryPrompt.isBlank() || prompts.isEmpty()) {
            return emptyList()
        }

        val queryTokens = TextUtils.tokenize(queryPrompt)
        val queryTfidf = calculateTfidf(queryTokens)

        // Шаг 1: Быстрая фильтрация с помощью TF-IDF
        val candidates = dbTfidfVectors
            .mapIndexed { index, docTfidf ->
                TfidfCandidate(index, TextUtils.cosineSimilarity(queryTfidf, docTfidf))
            }
            .filter { it.cosineScore > 0.0 }
            .sortedByDescending { it.cosineScore }
            .take(tfidfTopN)

        // Шаг 2: Точная проверка кандидатов с помощью Левенштейна
        val results = candidates.map { candidate ->
            val candidatePrompt = prompts[candidate.index]
            val distance = TextUtils.levenshteinDistance(
                queryPrompt.lowercase(),
                candidatePrompt.lowercase()
            )
            val maxLength = max(queryPrompt.length, candidatePrompt.length)
            val similarity = if (maxLength == 0) 1.0 else 1.0 - distance.toDouble() / maxLength

            PromptMatchResult(
                originalPrompt = candidatePrompt,
                similarityScore = similarity,
                isPotentialDuplicate = similarity >= levenshteinThreshold
            )
        }

        return results.sortedByDescending { it.similarityScore }
    }

    private fun calculateIdf(): Map<String, Double> {
        val numDocuments = documentTokens.size.coerceAtLeast(1)
        val docFrequency = mutableMapOf<String, Int>()
        documentTokens.forEach { tokens ->
            tokens.toSet().forEach { word ->
                docFrequency[word] = docFrequency.getOrDefault(word, 0) + 1
            }
        }
        return docFrequency.mapValues { (_, count) ->
            kotlin.math.ln(numDocuments.toDouble() / (1 + count))
        }
    }

    private fun calculateTfidf(tokens: List<String>): Map<String, Double> {
        val numTokens = tokens.size.takeIf { it > 0 } ?: return emptyMap()
        val tf = tokens.groupingBy { it }.eachCount()
        return tf.mapValues { (word, count) ->
            (count.toDouble() / numTokens) * this.idf.getOrDefault(word, 0.0)
        }
    }
}