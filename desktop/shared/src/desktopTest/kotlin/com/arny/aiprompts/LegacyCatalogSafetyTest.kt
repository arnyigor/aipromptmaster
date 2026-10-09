@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.arny.aiprompts

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.PromptSynchronizerImpl
import com.arny.aiprompts.data.utils.ZipUtils
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.repositories.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LegacyCatalogSafetyTest {
    @Test fun releaseDoesNotLoadDebugImportBackups() = runTest {
        if (com.arny.aiprompts.presentation.navigation.MainComponent.IS_IMPORT_ENABLED) return@runTest
        synchronizer.loadLocalPrompts()
        coVerify(exactly = 0) { repository.savePrompts(any()) }
    }
    private val repository = mockk<IPromptsRepository>(relaxed = true)
    private val settings = mockk<ISettingsRepository>(relaxed = true)
    private val service = mockk<GitHubService>()
    private val synchronizer = spyk(PromptSynchronizerImpl(service, repository, settings))

    private fun prompt(id: String) = Prompt(
        id = id, title = "Same title", description = null, content = null,
        compatibleModels = emptyList(), category = "test", status = "active",
        isLocal = false, createdAt = null, modifiedAt = null
    )

    @Test
    fun `repeated imports retain all IDs even when titles already exist`() = runTest {
        val snapshot = listOf(prompt("one"), prompt("two"))
        coEvery { repository.getAllPrompts() } returns flowOf(snapshot)
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns snapshot
        repeat(2) { assertIs<SyncResult.Success>(synchronizer.synchronize(true)) }
        coVerify(exactly = 2) { repository.savePrompts(snapshot) }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
    }

    @Test
    fun `empty snapshot never changes the database`() = runTest {
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns emptyList()
        assertIs<SyncResult.Error>(synchronizer.synchronize(true))
        coVerify(exactly = 0) { repository.savePrompts(any()) }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
        coVerify(exactly = 0) { settings.setLastSyncTime(any()) }
    }

    @Test
    fun `duplicate IDs never change the database`() = runTest {
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns listOf(prompt("one"), prompt("one"))
        assertIs<SyncResult.Error>(synchronizer.synchronize(true))
        coVerify(exactly = 0) { repository.savePrompts(any()) }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
    }

    @Test
    fun `cancellation propagates without changing the database`() = runTest {
        coEvery { synchronizer.downloadAndProcessArchive(any()) } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> { synchronizer.synchronize(true) }
        coVerify(exactly = 0) { repository.savePrompts(any()) }
    }

    @Test
    fun `concurrent synchronizations do not overlap`() = runTest {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        coEvery { synchronizer.downloadAndProcessArchive(any()) } coAnswers {
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            try { delay(50); listOf(prompt("one")) } finally { active.decrementAndGet() }
        }
        listOf(async { synchronizer.synchronize(true) }, async { synchronizer.synchronize(true) })
            .awaitAll().forEach { assertIs<SyncResult.Success>(it) }
        kotlin.test.assertEquals(1, maximum.get())
    }

    @Test
    fun `one malformed JSON rejects the entire archive`() = runTest {
        unmockkObject(ZipUtils)
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            mapOf(
                "prompts/test/valid.json" to """{"id":"one","title":"Valid","content":{"en":"content"}}""",
                "prompts/test/broken.json" to "{broken"
            ).forEach { (path, json) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(json.toByteArray())
                zip.closeEntry()
            }
        }
        coEvery { service.downloadFile(any()) } returns bytes.toByteArray()
        val actual = PromptSynchronizerImpl(service, repository, settings)
        assertIs<SyncResult.Error>(actual.synchronize(true))
        coVerify(exactly = 0) { repository.savePrompts(any()) }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
    }
}
