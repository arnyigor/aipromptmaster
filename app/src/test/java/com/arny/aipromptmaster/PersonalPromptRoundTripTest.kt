package com.arny.aipromptmaster

import com.arny.aipromptmaster.data.mappers.*
import com.arny.promptcontract.*
import org.junit.Test
import kotlin.test.*

class PersonalPromptRoundTripTest {
    @Test fun vaultCanonicalRecordSurvivesAndroidDatabaseMappingAndIsoMilliseconds() {
        val prompt = canonicalPersonalPrompt(PromptJson(id = "personal", title = "Личный", isLocal = true,
            content = mapOf("ru" to "Текст", "en" to "Text", "fr" to "Texte"),
            variables = listOf(VariableJson("topic", "Описание", "Kotlin", examples = listOf("KMP"))),
            compatibleModels = listOf("first", "second"),
            promptVariants = listOf(PromptVariantJson(VariantIdJson("prompt", "variant", 3), mapOf("ru" to "Вариант"))),
            createdAt = "2026-10-09T01:02:03.123Z", updatedAt = "2026-10-09T02:03:04.456Z"))
        val restored = canonicalPersonalPrompt(prompt.toDomain().toEntity().toDomain().toExportJson())
        assertEquals(prompt, restored)
        val edited = prompt.toDomain().copy(promptVariants = listOf(prompt.promptVariants.single().copy(content = mapOf("ru" to "Исправлено")).toDomain()))
        assertEquals("Исправлено", edited.toExportJson().promptVariants.single().content["ru"])
    }
}
