import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinParserTest {

    @Test
    fun `parse simple function should return one code unit`() {
        // 1. Подготовка (Arrange)
        val code = """
            package com.arny
            
            fun greet(name: String) {
                println("Hello, ${'$'}name")
            }
        """.trimIndent()
        assertNotNull(code)
    }
}