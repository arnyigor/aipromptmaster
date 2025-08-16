package com.arny.aiprompts.presentation.ui.scraper

/**
 * Утилитарный объект для парсинга строки с номерами страниц.
 * Поддерживает форматы: "5", "1,2,3", "1-5", "1-3, 5, 8-10".
 */
object PageStringParser {

    /**
     * Преобразует строку в отсортированный список уникальных номеров страниц.
     * @param input Строка для парсинга. Например, "1-3, 5".
     * @return [List<Int>] отсортированных уникальных номеров страниц. Игнорирует некорректные вводы.
     */
    fun parse(input: String): List<Int> {
        // Используем Set для автоматического обеспечения уникальности страниц.
        val pages = mutableSetOf<Int>()

        // 1. Нормализуем строку: заменяем запятые пробелами и убираем лишние пробелы.
        val tokens = input.replace(",", " ").trim().split("\\s+".toRegex())

        for (token in tokens) {
            when {
                // 2. Случай диапазона: "1-5"
                "-" in token -> {
                    val rangeParts = token.split("-").map { it.toIntOrNull() }
                    val start = rangeParts.getOrNull(0)
                    val end = rangeParts.getOrNull(1)

                    if (start != null && end != null && start <= end) {
                        (start..end).forEach { pages.add(it) }
                    }
                }
                // 3. Случай одного числа: "5"
                else -> {
                    token.toIntOrNull()?.let { pages.add(it) }
                }
            }
        }

        // 4. Возвращаем отсортированный список
        return pages.sorted()
    }
}