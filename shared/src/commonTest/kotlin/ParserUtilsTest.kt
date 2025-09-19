import com.arny.aiprompts.data.parser.cleanHtmlToText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jsoup.Jsoup

class ParserUtilsTest {

    @Test
    fun `cleanHtmlToText returns empty string for null element`() {
        val result = cleanHtmlToText(null)
        assertEquals("", result)
    }

    @Test
    fun `cleanHtmlToText cleans simple text without html`() {
        val html = "<div>Simple text</div>"
        val element = Jsoup.parse(html).body().child(0)
        val result = cleanHtmlToText(element)
        assertEquals("Simple text", result)
    }

    @Test
    fun `cleanHtmlToText preserves line breaks from br tags`() {
        val html = "<div>Line 1<br>Line 2</div>"
        val element = Jsoup.parse(html).body().child(0)
        val result = cleanHtmlToText(element)
        assertEquals("Line 1\nLine 2", result)
    }

    @Test
    fun `cleanHtmlToText handles multiple br tags`() {
        val html = "<div>Line 1<br><br>Line 3</div>"
        val element = Jsoup.parse(html).body().child(0)
        val result = cleanHtmlToText(element)
        assertEquals("Line 1\n\nLine 3", result)
    }

    @Test
    fun `cleanHtmlToText trims whitespace`() {
        val html = "<div>  Text with spaces  </div>"
        val element = Jsoup.parse(html).body().child(0)
        val result = cleanHtmlToText(element)
        assertEquals("Text with spaces", result)
    }

    @Test
    fun `cleanHtmlToText handles nested elements`() {
        val html = "<div><p>Paragraph 1</p><p>Paragraph 2</p></div>"
        val element = Jsoup.parse(html).body().child(0)
        val result = cleanHtmlToText(element)
        assertEquals("Paragraph 1\nParagraph 2", result)
    }
}