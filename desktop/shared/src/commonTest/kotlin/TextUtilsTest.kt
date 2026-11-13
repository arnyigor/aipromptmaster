import com.arny.aiprompts.data.utils.TextUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextUtilsTest {

    @Test
    fun `tokenize splits text into words and converts to lowercase`() {
        val result = TextUtils.tokenize("Привет, мир! How are you?")
        val expected = listOf("привет", "мир", "how", "are", "you")
        assertEquals(expected, result)
    }

    @Test
    fun `tokenize handles empty string`() {
        val result = TextUtils.tokenize("")
        assertEquals(emptyList(), result)
    }

    @Test
    fun `tokenize handles punctuation only`() {
        val result = TextUtils.tokenize("!!! ??? ,,, ")
        assertEquals(emptyList(), result)
    }

    @Test
    fun `tokenize handles mixed languages`() {
        val result = TextUtils.tokenize("Hello мир 123 test")
        val expected = listOf("hello", "мир", "123", "test")
        assertEquals(expected, result)
    }

    @Test
    fun `levenshteinDistance returns 0 for identical strings`() {
        val result = TextUtils.levenshteinDistance("test", "test")
        assertEquals(0, result)
    }

    @Test
    fun `levenshteinDistance calculates correct distance for simple changes`() {
        assertEquals(1, TextUtils.levenshteinDistance("кот", "кит")) // substitution
        assertEquals(1, TextUtils.levenshteinDistance("test", "tests")) // insertion
        assertEquals(1, TextUtils.levenshteinDistance("test", "tes")) // deletion
    }

    @Test
    fun `levenshteinDistance handles empty strings`() {
        assertEquals(0, TextUtils.levenshteinDistance("", ""))
        assertEquals(4, TextUtils.levenshteinDistance("test", ""))
        assertEquals(4, TextUtils.levenshteinDistance("", "test"))
    }

    @Test
    fun `cosineSimilarity returns 1 for identical vectors`() {
        val vec = mapOf("word1" to 0.5, "word2" to 0.3)
        val result = TextUtils.cosineSimilarity(vec, vec)
        assertEquals(1.0, result, 0.0001)
    }

    @Test
    fun `cosineSimilarity returns 0 for orthogonal vectors`() {
        val vec1 = mapOf("word1" to 1.0)
        val vec2 = mapOf("word2" to 1.0)
        val result = TextUtils.cosineSimilarity(vec1, vec2)
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `cosineSimilarity returns 0 for empty vectors`() {
        val vec1 = emptyMap<String, Double>()
        val vec2 = mapOf("word" to 1.0)
        assertEquals(0.0, TextUtils.cosineSimilarity(vec1, vec2))
        assertEquals(0.0, TextUtils.cosineSimilarity(vec2, vec1))
        assertEquals(0.0, TextUtils.cosineSimilarity(vec1, vec1))
    }

    @Test
    fun `cosineSimilarity calculates correct similarity for overlapping vectors`() {
        val vec1 = mapOf("word1" to 1.0, "word2" to 0.0)
        val vec2 = mapOf("word1" to 1.0, "word3" to 0.0)
        val result = TextUtils.cosineSimilarity(vec1, vec2)
        assertTrue(result > 0.5) // Should be positive but less than 1
    }
}