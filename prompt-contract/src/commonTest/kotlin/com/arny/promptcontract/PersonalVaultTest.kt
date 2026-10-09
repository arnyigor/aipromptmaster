package com.arny.promptcontract

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PersonalVaultTest {
    private fun prompt(id: String, text: String = id) = PromptJson(id = id, title = id, isLocal = true, content = mapOf("ru" to text))
    private fun snap(vararg prompts: PromptJson) = PersonalVaultSnapshot(prompts = prompts.toList()).checked()
    private val hash: (ByteArray) -> String = { it.decodeToString() }
    private fun hashes(snapshot: PersonalVaultSnapshot) = snapshot.prompts.associate { it.id!! to hash(Json.encodeToString(PromptJson.serializer(), it).encodeToByteArray()) }
    @Test fun firstSyncMergesDistinctPromptsButRequiresChoiceForSameId() {
        val plan = planPersonalVault(snap(prompt("a"), prompt("both", "local")), PersonalVaultRemoteSnapshot(snap(prompt("b"), prompt("both", "remote")), "sha"), emptyMap(), hash)
        assertEquals(setOf("a", "b"), plan.merged.keys)
        assertEquals(listOf("both"), plan.conflicts.map { it.id })
    }
    @Test fun baselinePropagatesEditsAndDeletionsInBothDirections() {
        val base = snap(prompt("local-edit"), prompt("remote-edit"), prompt("delete-local"), prompt("delete-remote"))
        val local = snap(prompt("local-edit", "new local"), prompt("remote-edit"), prompt("delete-remote"))
        val remote = snap(prompt("local-edit"), prompt("remote-edit", "new remote"), prompt("delete-local"))
        val plan = planPersonalVault(local, PersonalVaultRemoteSnapshot(remote, "sha"), hashes(base), hash)
        assertTrue(plan.conflicts.isEmpty())
        assertEquals(mapOf("local-edit" to "new local", "remote-edit" to "new remote"), plan.merged.mapValues { it.value.content.getValue("ru") })
    }
    @Test fun deletionAgainstModificationRequiresExplicitChoice() {
        val base = snap(prompt("a"))
        val plan = planPersonalVault(snap(), PersonalVaultRemoteSnapshot(snap(prompt("a", "edited")), "sha"), hashes(base), hash)
        assertEquals(1, plan.conflicts.size)
        assertNull(plan.conflicts.single().local)
    }
    private class Store : PersonalVaultStore {
        var config: PersonalVaultConfig? = PersonalVaultConfig("owner/private", "main", "test-only-token")
        var baseline: PersonalVaultBaseline? = null
        override fun loadPersonalVault() = config
        override fun savePersonalVault(config: PersonalVaultConfig) { this.config = config }
        override fun disconnectPersonalVault() { config = null }
        override fun loadPersonalVaultBaseline() = baseline
        override fun savePersonalVaultBaseline(baseline: PersonalVaultBaseline) { this.baseline = baseline }
    }
    private class Local(var value: PersonalVaultSnapshot) : PersonalVaultLocal {
        var applied = 0
        override suspend fun snapshot() = value
        override suspend fun apply(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) { check(value == expected); value = result; applied++ }
    }
    private inner class Remote(var value: PersonalVaultRemoteSnapshot) : PersonalVaultRemote {
        var writes = 0; var failWrite = false
        override suspend fun read(config: PersonalVaultConfig) = value
        override suspend fun write(config: PersonalVaultConfig, snapshot: PersonalVaultSnapshot, expectedSha: String?) {
            check(!failWrite) { "GitHub changed" }; assertEquals(value.sha, expectedSha); writes++; value = PersonalVaultRemoteSnapshot(snapshot, "updated-sha")
        }
        override fun hash(value: ByteArray) = value.decodeToString()
    }
    @Test fun remoteSuccessLocalFailureHasPersistentRecoveryStageAndCanBeRetried() = runTest {
        val values = mutableMapOf<String, String>()
        val preferences = PersonalVaultPreferences(values::get) { key, value -> values[key] = value }
        preferences.savePersonalVault(PersonalVaultConfig("owner/private", "main", "test-token"))
        var value = snap(prompt("a")); var fail = true
        val local = object : PersonalVaultLocal {
            override suspend fun snapshot() = value
            override suspend fun apply(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) {
                if (fail) error("Synthetic local failure")
                assertEquals(expected, value); value = result
            }
        }
        val remote = Remote(PersonalVaultRemoteSnapshot(snap(prompt("b")), "sha"))
        val manager = PersonalVaultManager(preferences, local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(1, remote.writes); assertEquals(snap(prompt("a")), value)
        assertEquals("GitHub обновлён", preferences.loadSyncStage())
        assertTrue(manager.state.value.message!!.contains("GitHub обновлён"))
        assertTrue(PersonalVaultManager(preferences, local, remote, this).state.value.message!!.contains("не завершена"))
        fail = false
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(remote.value.snapshot, value); assertNull(preferences.loadSyncStage())
        assertNotNull(preferences.lastSuccessfulSync())
    }

    @Test fun baselineFailureAfterLocalApplyCanBeRetriedWithoutAnotherRemoteWrite() = runTest {
        val values = mutableMapOf<String, String>(); var fail = false
        val preferences = PersonalVaultPreferences(values::get) { key, value ->
            if (fail && key == "personal_vault_base_marker") error("Synthetic baseline failure")
            values[key] = value
        }
        preferences.savePersonalVault(PersonalVaultConfig("owner/private", "main", "test-token"))
        val local = Local(snap(prompt("a")))
        val remote = Remote(PersonalVaultRemoteSnapshot(snap(prompt("b")), "sha"))
        val manager = PersonalVaultManager(preferences, local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle(); fail = true
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(local.value, remote.value.snapshot); assertEquals(1, remote.writes)
        assertNotNull(preferences.loadSyncStage()); assertNull(preferences.lastSuccessfulSync())
        fail = false
        val restored = PersonalVaultManager(preferences, local, remote, this)
        restored.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        restored.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(1, remote.writes); assertNull(preferences.loadSyncStage())
        assertNotNull(preferences.loadPersonalVaultBaseline()); assertNotNull(preferences.lastSuccessfulSync())
    }

    @Test fun previewDoesNotPublishAndExplicitApplyUnifiesBothSides() = runTest {
        val store = Store(); val local = Local(snap(prompt("a"))); val remote = Remote(PersonalVaultRemoteSnapshot(snap(prompt("b")), "old"))
        val manager = PersonalVaultManager(store, local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        assertTrue(manager.state.value.ready); assertEquals(0, remote.writes); assertEquals(0, local.applied)
        assertEquals(2, manager.state.value.changes.size)
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(setOf("a", "b"), local.value.prompts.map { it.id }.toSet())
        assertEquals(local.value, remote.value.snapshot); assertNotNull(store.baseline)
    }
    @Test fun localEditsAfterPreviewBlockRemoteWrite() = runTest {
        val local = Local(snap(prompt("a"))); val remote = Remote(PersonalVaultRemoteSnapshot(snap(), null))
        val manager = PersonalVaultManager(Store(), local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle(); local.value = snap(prompt("a", "edited"))
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(0, remote.writes); assertEquals(0, local.applied); assertFalse(manager.state.value.ready)
    }
    @Test fun remoteConflictDoesNotChangeLocalLibraryOrBaseline() = runTest {
        val store = Store(); val local = Local(snap(prompt("a"))); val remote = Remote(PersonalVaultRemoteSnapshot(snap(prompt("b")), "sha")); remote.failWrite = true
        val manager = PersonalVaultManager(store, local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(snap(prompt("a")), local.value); assertNull(store.baseline); assertEquals(0, local.applied)
    }
    @Test fun missingTrackedRemoteFileCannotEraseLocalPrompts() = runTest {
        val store = Store(); store.baseline = PersonalVaultBaseline(store.config!!.target, hashes(snap(prompt("a"))))
        val manager = PersonalVaultManager(store, Local(snap(prompt("a"))), Remote(PersonalVaultRemoteSnapshot(snap(), null)), this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        assertFalse(manager.state.value.ready); assertTrue(manager.state.value.message!!.contains("удалён"))
    }
    @Test fun unresolvedConflictBlocksApplyAndChoiceMayKeepDeletion() = runTest {
        val store = Store(); store.baseline = PersonalVaultBaseline(store.config!!.target, hashes(snap(prompt("a"))))
        val local = Local(snap()); val remote = Remote(PersonalVaultRemoteSnapshot(snap(prompt("a", "new")), "sha"))
        val manager = PersonalVaultManager(store, local, remote, this)
        manager.onAction(PersonalVaultAction.Preview); advanceUntilIdle()
        manager.onAction(PersonalVaultAction.Resolve("a", false))
        assertEquals("удалить", manager.state.value.changes.single().github)
        manager.onAction(PersonalVaultAction.Apply); advanceUntilIdle()
        assertEquals(snap(), remote.value.snapshot)
    }
    @Test fun preferenceBaselineSurvivesManyChunksAndReusesTwoSlots() {
        val values = mutableMapOf<String, String>(); val store = PersonalVaultPreferences(values::get) { key, value -> values[key] = value }
        val base = PersonalVaultBaseline("owner/repo@main", (1..1000).associate { "prompt-$it" to "a".repeat(64) })
        repeat(5) { store.savePersonalVaultBaseline(base); assertEquals(base, store.loadPersonalVaultBaseline()) }
        assertTrue(values.values.all { it.length <= 3000 }); assertTrue(values.keys.none { it.startsWith("personal_vault_base_2_") })
    }
    @Test fun publicRecordsAndInvalidRepositoriesAreRejectedAndTokensRedacted() {
        assertFailsWith<IllegalArgumentException> { snap(prompt("a").copy(isLocal = false)).checked() }
        assertFailsWith<IllegalArgumentException> { PersonalVaultConfig("../repo", token = "secret").checked() }
        assertFalse(PersonalVaultConfig("owner/repo", token = "secret").toString().contains("secret"))
    }
    @Test fun disconnectRetainsLocalLibraryAndStopsConfiguredSync() = runTest {
        val store = Store(); val local = Local(snap(prompt("a"))); val remote = Remote(PersonalVaultRemoteSnapshot(snap(), null))
        val manager = PersonalVaultManager(store, local, remote, this)
        manager.onAction(PersonalVaultAction.Disconnect); advanceUntilIdle()
        assertNull(store.config); assertEquals(snap(prompt("a")), local.value); assertEquals(0, remote.writes)
    }
    @Test fun canonicalBytesHaveStableMapOrderAndMissingDatesAcrossDevices() {
        val first = canonicalPersonalPrompt(prompt("a").copy(content = linkedMapOf("ru" to "Текст", "en" to "Text")))
        val second = canonicalPersonalPrompt(prompt("a").copy(content = linkedMapOf("en" to "Text", "ru" to "Текст")))
        assertEquals(Json.encodeToString(PromptJson.serializer(), first), Json.encodeToString(PromptJson.serializer(), second))
        assertEquals("1970-01-01T00:00:00Z", first.createdAt)
    }
}
