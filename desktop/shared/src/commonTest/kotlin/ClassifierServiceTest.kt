import com.arny.aiprompts.domain.model.ReferencePrompt
import com.arny.aiprompts.domain.usecase.PromptClassifier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifierServiceTest {

    private lateinit var classifier: PromptClassifier

    @BeforeTest
    fun setup() {
        val testData = listOf(
            // Создание изображений
            ReferencePrompt("1", "Создание изображений", "Нарисуй кота в космосе"),
            ReferencePrompt("2", "Создание изображений", "Сгенерируй футуристический пейзаж"),
            ReferencePrompt("3", "Создание изображений", "Создай изображение собаки на пляже"),
            ReferencePrompt("4", "Создание изображений", "Нарисуй фэнтезийный замок в горах"),

            // Написание кода
            ReferencePrompt("5", "Написание кода", "Напиши функцию сортировки на Python"),
            ReferencePrompt("6", "Написание кода", "Как проверить тип переменной в JS?"),
            ReferencePrompt("7", "Написание кода", "Создай REST API на Node.js"),
            ReferencePrompt("8", "Написание кода", "Напиши алгоритм поиска в ширину"),

            // Анализ и суммаризация текста
            ReferencePrompt("9", "Анализ и суммаризация текста", "Сделай выжимку из текста"),
            ReferencePrompt("10", "Анализ и суммаризация текста", "Какие основные идеи в этой статье?"),
            ReferencePrompt("11", "Анализ и суммаризация текста", "Проанализируй этот документ и выдели ключевые моменты"),
            ReferencePrompt("12", "Анализ и суммаризация текста", "Составь краткое содержание книги"),

            // Перевод
            ReferencePrompt("13", "Перевод", "Переведи фразу на английский"),
            ReferencePrompt("14", "Перевод", "Как будет 'спасибо' по-французски?"),
            ReferencePrompt("15", "Перевод", "Переведи этот текст с немецкого"),
            ReferencePrompt("16", "Перевод", "Сделай перевод с китайского на русский")
        )
        classifier = PromptClassifier(testData)
    }

    @Test
    fun `test image generation classification`() {
        val result = classifier.classify("Нарисуй собаку на луне")
        assertEquals("Создание изображений", result.predictedCategory)
        assertTrue(result.confidence > 0.3, "Confidence should be greater than 0.3, but was ${result.confidence}")
    }

    @Test
    fun `test code writing classification`() {
        val result = classifier.classify("Как написать цикл в Python?")
        assertEquals("Написание кода", result.predictedCategory)
        assertTrue(result.confidence > 0.3, "Confidence should be greater than 0.3, but was ${result.confidence}")
    }

    @Test
    fun `test text analysis classification`() {
        val result = classifier.classify("Сделай краткое содержание этого текста")
        assertEquals("Анализ и суммаризация текста", result.predictedCategory)
        assertTrue(result.confidence > 0.3, "Confidence should be greater than 0.3, but was ${result.confidence}")
    }

    @Test
    fun `test translation classification`() {
        val result = classifier.classify("Переведи это предложение на японский")
        assertEquals("Перевод", result.predictedCategory)
        assertTrue(result.confidence > 0.3, "Confidence should be greater than 0.3, but was ${result.confidence}")
    }

    @Test
    fun `test undefined category for unknown prompt`() {
        val result = classifier.classify("Как приготовить борщ по старинному рецепту?")
        // Проверяем, что категория Undefined или уверенность низкая
        assertTrue(
            result.predictedCategory == "Undefined" || result.confidence <= 0.5,
            "Should be undefined or low confidence, but got category: ${result.predictedCategory} with confidence: ${result.confidence}"
        )
    }

    @Test
    fun `test empty prompt returns undefined`() {
        val result = classifier.classify("")
        assertEquals("Undefined", result.predictedCategory)
        assertEquals(0.0, result.confidence, "Empty prompt should have 0.0 confidence")
    }

    @Test
    fun `test whitespace prompt returns undefined`() {
        val result = classifier.classify("   ")
        assertEquals("Undefined", result.predictedCategory)
        assertEquals(0.0, result.confidence, "Whitespace prompt should have 0.0 confidence")
    }
}