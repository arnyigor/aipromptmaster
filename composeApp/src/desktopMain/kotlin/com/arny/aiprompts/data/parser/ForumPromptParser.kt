package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.PostType
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * JVM-специфичная реализация интерфейса IForumPromptParser.
 * Находится в desktopMain, так как использует Jsoup - JVM библиотеку.
 *
 * @param classifier Компонент, отвечающий за определение типа контента поста.
 * @param strategyFactory Фабрика, предоставляющая нужную стратегию парсинга в зависимости от типа поста.
 */
class ForumPromptParserImpl(
    private val classifier: PostClassifier,
    private val strategyFactory: (PostType) -> ParsingStrategy
) : IForumPromptParser {

    /**
     * Основной метод, который оркестрирует процесс парсинга HTML-контента.
     * Он выполняется в фоновом потоке IO для предотвращения блокировки UI.
     *
     * @param htmlContent HTML-содержимое страницы в виде строки.
     * @return Список объектов PromptData, извлеченных со страницы.
     */
    override suspend fun parse(htmlContent: String): List<PromptData> = withContext(Dispatchers.IO) {
        // 1. Парсим всю HTML строку в объект Document с помощью Jsoup
        val document = Jsoup.parse(htmlContent)

        // 2. Находим все элементы, соответствующие селектору контейнера поста
        val postElements: List<Element> = document.select("table.ipbtable[data-post]")

        // 3. Асинхронно обрабатываем каждый найденный элемент поста
        postElements.map { postElement ->
            // async запускает каждую задачу парсинга в отдельной корутине,
            // что позволяет обрабатывать их параллельно.
            async {
                try {
                    // 3.1. Определяем тип поста (стандартный, с файлом, джейлбрейк и т.д.)
                    val postType = classifier.classify(postElement)

                    // 3.2. Получаем из фабрики соответствующую этому типу стратегию парсинга
                    val strategy = strategyFactory(postType)

                    // 3.3. Вызываем метод parse у стратегии, чтобы извлечь данные
                    strategy.parse(postElement)
                } catch (e: Exception) {
                    // В случае любой ошибки при парсинге одного поста, мы не роняем весь процесс,
                    // а просто логируем ошибку и возвращаем null.
                    println("Ошибка парсинга поста ${postElement.attr("data-post")}: ${e.message}")
                    e.printStackTrace()
                    null
                }
            }
        }.awaitAll().filterNotNull() // 4. Ждем завершения всех задач и отфильтровываем те, что вернули null (ошибки или не-промпты)
    }
}