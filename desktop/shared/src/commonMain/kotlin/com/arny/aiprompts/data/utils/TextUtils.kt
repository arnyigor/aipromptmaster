package com.arny.aiprompts.data.utils

import kotlin.math.sqrt

/**
 * Утилитарный объект для текстовой обработки и вычисления метрик схожести.
 *
 * Содержит реализации алгоритмов для токенизации текста, вычисления расстояния Левенштейна
 * и косинусного сходства между векторами.
 */
object TextUtils {

    /**
     * Разбивает текст на токены (слова).
     *
     * Извлекает все последовательности букв и цифр, игнорируя знаки препинания.
     * Результат приводится к нижнему регистру.
     *
     * @param text Входной текст для токенизации
     * @return Список токенов (слов) в нижнем регистре
     *
     * @sample
     * ```
     * val tokens = TextUtils.tokenize("Привет, мир! How are you?")
     * // Result: ["привет", "мир", "how", "are", "you"]
     * ```
     */
    fun tokenize(text: String): List<String> {
        val regex = Regex("""\b[\wа-яА-ЯёЁ]+\b""")
        return regex.findAll(text.lowercase()).map { it.value }.toList()
    }

    /**
     * Вычисляет расстояние Левенштейна (редакционное расстояние) между двумя строками.
     *
     * Расстояние Левенштейна — это минимальное количество операций вставки, удаления
     * и замены, необходимых для превращения одной строки в другую.
     *
     * @param s1 Первая строка
     * @param s2 Вторая строка
     * @return Расстояние Левенштейна между строками (0 означает идентичность)
     *
     * @sample
     * ```
     * val distance = TextUtils.levenshteinDistance("кот", "кит")
     * // Result: 1 (нужно заменить 'о' на 'и')
     * ```
     */
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

    /**
     * Вычисляет косинусное сходство между двумя векторами.
     *
     * Косинусное сходство измеряет угол между двумя векторами и возвращает значение
     * от 0 (перпендикулярные векторы) до 1 (идентичные направления).
     * Используется для сравнения TF-IDF векторов в задачах текстовой аналитики.
     *
     * @param vec1 Первый вектор в формате Map<термин, вес>
     * @param vec2 Второй вектор в формате Map<термин, вес>
     * @return Косинусное сходство (значение от 0.0 до 1.0)
     *
     * @sample
     * ```
     * val vec1 = mapOf("кошка" to 0.5, "собака" to 0.3)
     * val vec2 = mapOf("кошка" to 0.4, "кот" to 0.2)
     * val similarity = TextUtils.cosineSimilarity(vec1, vec2)
     * ```
     */
    fun cosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        val intersection = vec1.keys.intersect(vec2.keys)
        val dotProduct = intersection.sumOf { word -> vec1.getValue(word) * vec2.getValue(word) }
        val normVec1 = sqrt(vec1.values.sumOf { it * it })
        val normVec2 = sqrt(vec2.values.sumOf { it * it })

        return if (normVec1 == 0.0 || normVec2 == 0.0) 0.0 else dotProduct / (normVec1 * normVec2)
    }
}