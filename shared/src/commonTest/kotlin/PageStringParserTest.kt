import com.arny.aiprompts.presentation.ui.scraper.PageStringParser
import kotlin.test.Test
import kotlin.test.assertEquals

class PageStringParserTest {

    @Test
    fun `parser correctly handles complex input`() {
        val input = " 1-3, 8, 2, 5-6 "
        val expected = listOf(1, 2, 3, 5, 6, 8)
        val result = PageStringParser.parse(input)
        assertEquals(expected, result)
    }

    @Test
    fun `parser correctly handles simple input`() {
        val input = "13-20"
        val expected = listOf(13, 14, 15, 16, 17, 18, 19, 20)
        val result = PageStringParser.parse(input)
        assertEquals(expected, result)
    }

    @Test
    fun `parser incorrect handles complex input`() {
        val input = " 1-, -, , "
        val expected = emptyList<Int>()
        val result = PageStringParser.parse(input)
        assertEquals(expected, result)
    }
}