package com.arny.aiprompts.domain.usecase
import com.arny.aiprompts.data.utils.TextUtils
import com.arny.aiprompts.domain.model.ClassificationResult
import com.arny.aiprompts.domain.model.NeighborInfo
import com.arny.aiprompts.domain.model.ReferencePrompt
import kotlin.math.ln

class PromptClassifier(private val referencePrompts: List<ReferencePrompt>) {

    private data class TfidfCandidate(val index: Int, val cosineScore: Double)

    private val documentTokens: List<List<String>> = referencePrompts.map { TextUtils.tokenize(it.prompt) }
    private val idf: Map<String, Double>
    private val dbTfidfVectors: List<Map<String, Double>>
    private val categories: List<String>

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.4
        const val DEFAULT_K = 5
        const val UNDEFINED_CATEGORY = "Undefined"
    }

    init {
        this.idf = calculateIdf()
        this.dbTfidfVectors = this.documentTokens.map { calculateTfidf(it) }
        this.categories = referencePrompts.map { it.category }.distinct()
    }

    fun classify(
        promptText: String,
        k: Int = DEFAULT_K,
        confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD
    ): ClassificationResult {
        if (promptText.isBlank() || referencePrompts.isEmpty()) {
            return ClassificationResult(
                predictedCategory = UNDEFINED_CATEGORY,
                confidence = 0.0,
                neighbors = emptyList()
            )
        }

        val queryTokens = TextUtils.tokenize(promptText)
        val queryTfidf = calculateTfidf(queryTokens)

        // Найти k ближайших соседей
        val candidates = dbTfidfVectors
            .mapIndexed { index, docTfidf ->
                TfidfCandidate(index, TextUtils.cosineSimilarity(queryTfidf, docTfidf))
            }
            .filter { it.cosineScore > 0.0 }
            .sortedByDescending { it.cosineScore }
            .take(k)

        if (candidates.isEmpty()) {
            return ClassificationResult(
                predictedCategory = UNDEFINED_CATEGORY,
                confidence = 0.0,
                neighbors = emptyList()
            )
        }

        // Подсчет голосов
        val neighborInfos = candidates.map { candidate ->
            val refPrompt = referencePrompts[candidate.index]
            NeighborInfo(
                prompt = refPrompt.prompt,
                category = refPrompt.category,
                similarity = candidate.cosineScore
            )
        }

        val categoryVotes = neighborInfos.groupingBy { it.category }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        val topCategory = categoryVotes.firstOrNull()
        val confidence = if (topCategory != null) {
            topCategory.second.toDouble() / k
        } else {
            0.0
        }

        val predictedCategory = if (confidence >= confidenceThreshold) {
            topCategory?.first ?: UNDEFINED_CATEGORY
        } else {
            UNDEFINED_CATEGORY
        }

        return ClassificationResult(
            predictedCategory = predictedCategory,
            confidence = confidence,
            neighbors = neighborInfos
        )
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
            ln(numDocuments.toDouble() / (1 + count))
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