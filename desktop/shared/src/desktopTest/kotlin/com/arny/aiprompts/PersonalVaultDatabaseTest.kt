package com.arny.aiprompts

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.db.entities.PromptEntity
import com.arny.aiprompts.data.repositories.DesktopPersonalVaultLocal
import com.arny.promptcontract.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.io.File
import kotlin.test.*
import io.mockk.*

class PersonalVaultDatabaseTest {
    @Test fun transactionalApplyUpdatesOnlyPersonalRecordsAndRejectsStalePreviewAndPublicIdCollision() = runTest {
        val directory = Files.createTempDirectory("personal-vault-room").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = File(directory, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.promptDao()
            dao.insertPrompts(listOf(PromptEntity(id = "public", title = "Public", description = "", status = "active", isLocal = false),
                PromptEntity(id = "personal", title = "Personal", description = "", status = "active", isLocal = true)))
            val settings = mockk<com.arny.aiprompts.data.repositories.ISettingsRepository>(relaxed = true)
            val vault = DesktopPersonalVaultLocal(dao, settings)
            val original = vault.snapshot()
            val incoming = PromptJson(id = "new", title = "Новое", isLocal = true, category = "general", status = "active", content = mapOf("ru" to "Текст", "fr" to "Texte"), createdAt = "2026-10-09T01:02:03.123Z", updatedAt = "2026-10-09T01:02:03.123Z")
            val result = PersonalVaultSnapshot(prompts = listOf(incoming))
            vault.validate(original, result); vault.apply(original, result)
            verify { settings.markLegacyPersonalFilesLoaded() }
            assertNull(dao.getById("personal")); assertEquals("Public", dao.getById("public")?.title)
            assertEquals("Текст", dao.getById("new")?.contentRu)
            assertEquals("Texte", vault.snapshot().prompts.single().content["fr"])
            val next = vault.snapshot()
            assertFailsWith<IllegalArgumentException> { vault.apply(original, PersonalVaultSnapshot()) }
            assertEquals(next, vault.snapshot())
            val collision = PersonalVaultSnapshot(prompts = listOf(incoming.copy(id = "public")))
            assertFailsWith<IllegalArgumentException> { vault.validate(next, collision) }
            assertFailsWith<IllegalArgumentException> { vault.apply(next, collision) }
            assertEquals("Public", dao.getById("public")?.title); assertEquals(next, vault.snapshot())
        } finally { db.close(); directory.deleteRecursively() }
    }
}
