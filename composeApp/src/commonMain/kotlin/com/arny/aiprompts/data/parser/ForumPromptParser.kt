package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.PostType
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

// Основной интерфейс, как в ТЗ
interface IForumPromptParser {
    suspend fun parse(htmlContent: String): List<PromptData>
}

class ForumPromptParser(
    private val classifier: PostClassifier,
    // Передаем "фабрику" стратегий для гибкости
    private val strategyFactory: (PostType) -> ParsingStrategy
) : IForumPromptParser {

    override suspend fun parse(htmlContent: String): List<PromptData> = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(htmlContent)
        val postElements = document.select("table.ipbtable[data-post]")

        // Запускаем парсинг каждого поста в отдельной асинхронной задаче
        postElements.map { postElement ->
            async { // async позволяет возвращать результат (в отличие от launch)
                try {
                    val postType = classifier.classify(postElement)
                    val strategy = strategyFactory(postType)
                    strategy.parse(postElement)
                } catch (e: Exception) {
                    println("Ошибка парсинга поста ${postElement.attr("data-post")}: ${e.message}")
                    null // В случае ошибки возвращаем null
                }
            }
        }.awaitAll().filterNotNull() // Ждем завершения всех задач и отфильтровываем неудачные
    }
}