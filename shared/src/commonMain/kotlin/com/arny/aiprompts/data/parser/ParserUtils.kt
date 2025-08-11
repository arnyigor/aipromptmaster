package com.arny.aiprompts.data.parser

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * "Умная" функция для очистки HTML-элемента и преобразования его в форматированный plain text.
 *
 * @param element Jsoup-элемент, который нужно очистить. Может быть null.
 * @return Очищенный текст с сохраненными переносами строк.
 */
fun cleanHtmlToText(element: Element?): String {
    if (element == null) return ""

    // Создаем клон, чтобы не изменять оригинальный DOM, что может повлиять на другие селекторы
    val cleanElement = element.clone()

    // 1. Заменяем <br> на символ переноса строки. Это ключевой шаг для сохранения абзацев.
    cleanElement.select("br").forEach { it.replaceWith(TextNode("\n")) }
    
    // 2. Jsoup автоматически обрабатывает блочные теги типа <p>, <div>, добавляя переносы.
    // Используем .text(), который извлекает весь видимый текст.
    // trim() убирает лишние пробелы и переносы в начале и конце.
    return cleanElement.text().trim()
}