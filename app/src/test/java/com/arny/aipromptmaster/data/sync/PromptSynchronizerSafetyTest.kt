package com.arny.aipromptmaster.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PromptSynchronizerSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private val service = mockk<GitHubService>()
    private val repository = mockk<IPromptsRepository>(relaxed = true)

    private fun synchronizer(files: Map<String, String>): PromptSynchronizerImpl {
        val context = mockk<Context>()
        every { context.cacheDir } returns temp.root
        val settings = mockk<SharedPreferences>()
        every { settings.all } returns emptyMap()
        val prefs = mockk<Prefs>(relaxed = true)
        every { prefs.settings } returns settings
        coEvery { repository.observeAllPrompts() } returns flowOf(emptyList())
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
        coEvery { service.downloadFile(any()) } coAnswers { Response.success(bytes.toByteArray().toResponseBody()) }
        return PromptSynchronizerImpl(service, repository, prefs, context)
    }

    private val valid = """{"id":"one","title":"Valid","content":{"en":"text"},"is_favorite":true,"is_local":true}"""

    @Test fun `valid ZIP upserts without deletions or remote local flags`() = runTest {
        assertIs<SyncResult.Success>(synchronizer(mapOf("prompts/test/one.json" to valid)).synchronize())
        coVerify(exactly = 1) {
            repository.syncPrompts(match { it.size == 1 && !it.single().isLocal && !it.single().isFavorite }, emptyList())
        }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
    }

    @Test fun `one malformed record rejects the entire archive`() = runTest {
        assertIs<SyncResult.Error>(synchronizer(mapOf("one.json" to valid, "broken.json" to "{broken")).synchronize())
        coVerify(exactly = 0) { repository.syncPrompts(any(), any()) }
        coVerify(exactly = 0) { repository.deletePromptsByIds(any()) }
    }

    @Test fun `empty archive does not change the database`() = runTest {
        assertIs<SyncResult.Error>(synchronizer(emptyMap()).synchronize())
        coVerify(exactly = 0) { repository.syncPrompts(any(), any()) }
    }

    @Test fun `duplicate IDs do not change the database`() = runTest {
        assertIs<SyncResult.Error>(synchronizer(mapOf("one.json" to valid, "two.json" to valid)).synchronize())
        coVerify(exactly = 0) { repository.syncPrompts(any(), any()) }
    }

    @Test fun `archive traversal cannot write outside extraction directory`() = runTest {
        assertIs<SyncResult.Error>(synchronizer(mapOf("../escaped.json" to valid)).synchronize())
        kotlin.test.assertFalse(java.io.File(temp.root, "escaped.json").exists())
        coVerify(exactly = 0) { repository.syncPrompts(any(), any()) }
    }

    @Test fun `cancellation is propagated`() = runTest {
        val actual = synchronizer(mapOf("one.json" to valid))
        coEvery { service.downloadFile(any()) } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> { actual.synchronize() }
        coVerify(exactly = 0) { repository.syncPrompts(any(), any()) }
    }
}
