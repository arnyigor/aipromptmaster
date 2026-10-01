package com.arny.promptcontract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogManifestTest {
    private val data = """{"id":"a","title":"Title"}""".encodeToByteArray()
    private val path = "prompts/general/a.json"
    private val manifest = CatalogManifest(1, "a".repeat(64), "commit", 1,
        listOf(CatalogEntry("a", path, "expected")), listOf("deleted"))

    @Test fun validatesCompleteSnapshotAndExplicitTombstones() {
        assertEquals(listOf("deleted"), CatalogManifest.verify(Json.encodeToString(manifest), mapOf(path to data)) { "expected" }.deletedIds)
    }

    @Test fun rejectsTruncatedTamperedAndExtraFiles() {
        assertFailsWith<IllegalArgumentException> { CatalogManifest.verify(Json.encodeToString(manifest), emptyMap()) { "expected" } }
        assertFailsWith<IllegalArgumentException> { CatalogManifest.verify(Json.encodeToString(manifest), mapOf(path to data)) { "wrong" } }
        assertFailsWith<IllegalArgumentException> { CatalogManifest.verify(Json.encodeToString(manifest), mapOf(path to data, "extra.json" to data)) { "expected" } }
    }

    @Test fun rejectsIdentityConflictsAndUnsupportedVersion() {
        assertFailsWith<IllegalArgumentException> { CatalogManifest.verify(Json.encodeToString(manifest.copy(deletedIds = listOf("a"))), mapOf(path to data)) { "expected" } }
        assertFailsWith<IllegalArgumentException> { CatalogManifest.verify(Json.encodeToString(manifest.copy(schemaVersion = 2)), mapOf(path to data)) { "expected" } }
    }
}
