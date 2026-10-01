package com.arny.promptcontract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PromptStorageTest {
    @Test fun readsLegacyDesktopNamesWithoutLosingPriority() {
        val legacy = Json.decodeFromString<PromptJson>("""{"id":"a","title":"Title","isLocal":true,"isFavorite":true,"prompt_variants":[{"variantId":{"type":"prompt","id":"v"},"content":{"ru":"Text"},"priority":4}]}""")
        assertEquals(true, legacy.isLocal)
        assertEquals(true, legacy.isFavorite)
        assertEquals("v", legacy.promptVariants.single().variantId?.id)
        assertEquals(4, legacy.promptVariants.single().priority)
    }
    @Test fun readsOldMapsAndPreservesFullWireDocument() {
        assertEquals(mapOf("topic" to "Kotlin"), PromptStorage.decode("""{"topic":"Kotlin"}""").variables)
        val document = PromptJson(id = "id", title = "Title",
            variables = listOf(VariableJson("topic", type = "string", examples = listOf("Kotlin"), defaultValue = "KMP")),
            promptVariants = listOf(PromptVariantJson(VariantIdJson("prompt", "v", 3), mapOf("ru" to "Variant"))))
        val decoded = PromptStorage.decode(PromptStorage.encode(mapOf("topic" to "KMP"), document))
        assertEquals(document, decoded.document)
        assertEquals(document, Json.decodeFromString<PromptJson>(Json.encodeToString(document)))
    }
}
