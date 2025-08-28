package com.arny.aiprompts.data.utils

import kotlin.math.*

object TextUtils {

    fun tokenize(text: String): List<String> {
        val regex = Regex("""\b[\wа-яА-ЯёЁ]+\b""")
        return regex.findAll(text.lowercase()).map { it.value }.toList()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            dp[i][0] = i
        }
        for (j in 0..s2.length) {
            dp[0][j] = j
        }

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    fun cosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        val intersection = vec1.keys.intersect(vec2.keys)
        val dotProduct = intersection.sumOf { word -> vec1.getValue(word) * vec2.getValue(word) }
        val normVec1 = sqrt(vec1.values.sumOf { it * it })
        val normVec2 = sqrt(vec2.values.sumOf { it * it })

        return if (normVec1 == 0.0 || normVec2 == 0.0) 0.0 else dotProduct / (normVec1 * normVec2)
    }
}