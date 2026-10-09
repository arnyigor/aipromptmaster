package com.arny.promptcontract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogContractTest {
    @Test fun preservesUnknownAndVariantFields() {
        val document = CatalogContract.parse("""{"id":"a","title":"Title","future":{"x":1},"prompt_variants":[{"variant_id":{"id":"v"}}]}""")
        assertEquals("a", document.id)
        assertTrue("future" in document.fields)
        assertTrue("prompt_variants" in document.fields)
    }

    @Test fun rejectsInvalidIdentityBeforeAdaptersCanCoerceIt() {
        listOf("null", "42", "\" \"", "true").forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                CatalogContract.parse("""{"id":$value,"title":"Title"}""")
            }
        }
        assertFailsWith<IllegalArgumentException> { CatalogContract.parse("""{"id":"a"}""") }
    }

    @Test fun rejectsEmptyAndDuplicateSnapshotsButAllowsSameTitles() {
        assertFailsWith<IllegalArgumentException> { CatalogContract.validateSnapshot(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            CatalogContract.validateSnapshot(listOf("a" to "Title", "a" to "Other"))
        }
        CatalogContract.validateSnapshot(listOf("a" to "Title", "b" to "Title"))
    }
}
