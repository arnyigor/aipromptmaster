package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.RawPostData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SimpleParser.
 * Tests parsing of HTML content from 4PDA forum posts.
 */
class SimpleParserTest {

    private val parser = SimpleParser()

    /**
     * Test parsing HTML with div.post[data-post] format (current 4PDA format)
     */
    @Test
    fun `parse should extract posts from div post format`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body>
                <div class="post" data-post="12345">
                    <div class="postcolor">
                        <p>Some description text</p>
                        <div class="post-block spoil">
                            <div class="block-title">ПРОМПТ №1: Test Prompt</div>
                            <div class="block-body">
                                This is the prompt content
                            </div>
                        </div>
                    </div>
                    <span class="normalname">
                        <a href="https://4pda.to/forum/index.php?showuser=123">TestAuthor</a>
                    </span>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(1, result.size, "Should find 1 post")
        
        val post = result.first()
        assertEquals("12345", post.postId, "Post ID should be extracted")
        assertEquals("TestAuthor", post.author.name, "Author name should be extracted")
        assertTrue(post.fullHtmlContent.contains("ПРОМПТ №1"), "Content should contain prompt text")
        assertTrue(post.isLikelyPrompt, "Should be detected as likely prompt (contains 'промпт')")
    }

    /**
     * Test parsing multiple posts
     */
    @Test
    fun `parse should extract multiple posts`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="post" data-post="111">
                    <div class="postcolor">Content 1</div>
                    <span class="normalname"><a href="#">Author1</a></span>
                </div>
                <div class="post" data-post="222">
                    <div class="postcolor">Content 2</div>
                    <span class="normalname"><a href="#">Author2</a></span>
                </div>
                <div class="post" data-post="333">
                    <div class="postcolor">Content 3</div>
                    <span class="normalname"><a href="#">Author3</a></span>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(3, result.size, "Should find 3 posts")
        assertEquals(listOf("111", "222", "333"), result.map { it.postId })
    }

    /**
     * Test that posts without data-post attribute are skipped
     */
    @Test
    fun `parse should skip elements without data-post`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="post" data-post="111">
                    <div class="postcolor">Valid post</div>
                </div>
                <div class="post">
                    <div class="postcolor">No ID post</div>
                </div>
                <div class="not-post">
                    <div class="postcolor">Not a post</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(1, result.size, "Should find only 1 valid post")
        assertEquals("111", result.first().postId)
    }

    /**
     * Test prompt detection heuristic
     */
    @Test
    fun `parse should detect likely prompts`() {
        // Test with "промпт" keyword
        val htmlWithPrompt = """
            <html>
            <body>
                <div class="post" data-post="1">
                    <div class="postcolor">Это промпт для ChatGPT</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val resultWithPrompt = parser.parse(htmlWithPrompt)
        assertTrue(resultWithPrompt.first().isLikelyPrompt, "Should detect 'промпт' keyword")

        // Test with "prompt" keyword (English)
        val htmlWithEnglishPrompt = """
            <html>
            <body>
                <div class="post" data-post="2">
                    <div class="postcolor">This is a prompt for AI</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val resultWithEnglishPrompt = parser.parse(htmlWithEnglishPrompt)
        assertTrue(resultWithEnglishPrompt.first().isLikelyPrompt, "Should detect 'prompt' keyword")
    }

    /**
     * Test empty HTML handling
     */
    @Test
    fun `parse should handle empty HTML`() {
        val result = parser.parse("")
        assertTrue(result.isEmpty(), "Should return empty list for empty HTML")
    }

    /**
     * Test HTML without posts
     */
    @Test
    fun `parse should handle HTML without posts`() {
        val html = """
            <html>
            <body>
                <div class="other">No posts here</div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)
        assertTrue(result.isEmpty(), "Should return empty list when no posts found")
    }

    /**
     * Test attachment extraction
     */
    @Test
    fun `parse should extract attachments`() {
        // Note: href must be absolute URL or have base URL for absUrl to work
        val html = """
            <html>
            <head><base href="https://4pda.to/forum/"></head>
            <body>
                <div class="post" data-post="1">
                    <div class="postcolor">Content with file</div>
                    <a class="attach-file" href="https://4pda.to/forum/index.php?act=attach&type=post&id=123">file.txt (10 KB)</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)
        
        assertEquals(1, result.size)
        assertTrue(result.first().attachments.isNotEmpty(), "Should have attachments")
        assertEquals("file.txt (10 KB)", result.first().attachments.first().filename)
    }

    /**
     * Test post URL extraction
     */
    @Test
    fun `parse should extract post URL`() {
        val html = """
            <html>
            <body>
                <div class="post" data-post="12345">
                    <div class="postcolor">Content</div>
                    <a href="https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=12345">Post Link</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = parser.parse(html)
        
        assertTrue(result.first().postUrl?.contains("findpost") == true, 
            "Should extract post URL with findpost")
    }
}
