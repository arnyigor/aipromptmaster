@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.arny.aiprompts

import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.mappers.toEntity
import com.arny.aiprompts.data.mappers.toExportJson
import com.arny.promptcontract.PromptJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PromptWireRoundTripTest {
    @Test fun `DTO database adapter and personal export preserve variants and variable definitions`() {
        val original = Json.decodeFromString<PromptJson>("""{
          "id":"a","title":"Title","category":"general","content":{"ru":"Text"},
          "variables":[{"name":"topic","type":"string","description":"Topic","default_value":"Kotlin","examples":["KMP"]}],
          "prompt_variants":[{"variant_id":{"type":"prompt","id":"v","priority":3},"content":{"ru":"Variant"}}],
          "metadata":{"author":{"id":"author-id","name":"Author"}},"created_at":"2026-10-01T00:00:00Z"
        }""")
        val restored = original.toDomain().copy(isLocal = true, isFavorite = true).toEntity().toDomain()
        val export = restored.toExportJson()
        assertEquals(original.variables, export.variables)
        assertEquals(original.promptVariants, export.promptVariants)
        assertEquals("Kotlin", restored.variables["topic"])
        assertEquals("author-id", restored.metadata.author?.id)
        assertNotNull(restored.createdAt)
        assertEquals(true, export.isLocal)
        assertEquals(true, export.isFavorite)
    }
}
