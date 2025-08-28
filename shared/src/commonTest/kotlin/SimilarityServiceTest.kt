import com.arny.aiprompts.domain.usecase.PromptSimilarityClassifier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimilarityServiceTest {

    private lateinit var similarityService: PromptSimilarityClassifier

    @BeforeTest
    fun setup() {
        val testData = listOf(
            "Нарисуй кота в космосе",
            "Сгенерируй футуристический пейзаж",
            "Напиши функцию сортировки на Python",
            "Как проверить тип переменной в JS?",
            "Сделай выжимку из текста",
            "Какие основные идеи в этой статье?",
            "Переведи фразу на английский",
            "Как будет 'спасибо' по-французски?"
        )
        similarityService = PromptSimilarityClassifier(testData)
    }

    @Test
    fun `test exact duplicate detection`() {
        val results = similarityService.findSimilar("Нарисуй кота в космосе", levenshteinThreshold = 0.95)
        assertTrue(results.isNotEmpty(), "Should find at least one result")
        val topResult = results.first()
        assertTrue(topResult.isPotentialDuplicate, "Exact match should be potential duplicate")
        assertEquals(1.0, topResult.similarityScore, 0.01, "Exact match should have similarity 1.0")
    }

    @Test
    fun `test near duplicate detection`() {
        val results = similarityService.findSimilar("Нарисуй кота в космосе!", levenshteinThreshold = 0.8)
        assertTrue(results.isNotEmpty(), "Should find at least one result")
        val topResult = results.first()
        assertTrue(topResult.isPotentialDuplicate, "Near duplicate should be potential duplicate")
        assertTrue(topResult.similarityScore > 0.8, "Similarity should be above threshold, but was ${topResult.similarityScore}")
    }

    @Test
    fun `test no duplicate for different prompt`() {
        val results = similarityService.findSimilar("Как сварить кофе по-турецки?", levenshteinThreshold = 0.8)
        // Может быть пустым или без дубликатов
        if (results.isNotEmpty()) {
            assertFalse(results.any { it.isPotentialDuplicate }, "Should not find duplicates for unrelated prompt")
        }
    }

    @Test
    fun `test similarity ranking`() {
        val results = similarityService.findSimilar("Нарисуй космического кота")
        assertTrue(results.isNotEmpty(), "Should find at least one result")
        // Первый результат должен быть наиболее похожим
        if (results.size > 1) {
            assertTrue(
                results.first().similarityScore >= results.last().similarityScore,
                "Results should be sorted by similarity score (first: ${results.first().similarityScore}, last: ${results.last().similarityScore})"
            )
        }
    }

    @Test
    fun `test empty query returns empty list`() {
        val results = similarityService.findSimilar("")
        assertTrue(results.isEmpty(), "Empty query should return empty results")
    }

    @Test
    fun `test whitespace query returns empty list`() {
        val results = similarityService.findSimilar("   ")
        assertTrue(results.isEmpty(), "Whitespace query should return empty results")
    }

    @Test
    fun `test case insensitive matching`() {
        val results = similarityService.findSimilar("НАРИСУЙ КОТА В КОСМОСЕ")
        assertTrue(results.isNotEmpty(), "Should find results for uppercase query")
        val topResult = results.first()
        assertTrue(topResult.similarityScore > 0.8, "Should have high similarity for case-insensitive match")
    }
}