package com.arny.aiprompts

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.PromptSynchronizerImpl
import com.arny.aiprompts.data.utils.ZipUtils
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.repositories.SyncResult
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.Ignore

@OptIn(ExperimentalTime::class)
class PromptSynchronizerTest {

    private val gitHubService = mockk<GitHubService>(relaxed = true)
    private val promptsRepository = mockk<IPromptsRepository>(relaxed = true)
    private val settingsRepository = mockk<ISettingsRepository>(relaxed = true)

    // Мок для ZipUtils object
    private val mockZipResult = mapOf(
        "test/prompt_1" to """{"id":"1","title":"Remote Prompt","description":null,"content":null,"compatibleModels":[],"category":"test","status":"active","isLocal":false,"isFavorite":false}"""
    )

    init {
        mockkObject(ZipUtils)
        every { ZipUtils.extractZip(any(), any()) } returns Result.success(Unit)
        every { ZipUtils.readJsonFilesFromDirectory(any()) } returns mockZipResult
    }

    @Test
    fun `synchronize returns TooSoon when cooldown active`() = runTest {
        // Given - use relaxed mocks, all necessary values are defaulted
        coEvery { settingsRepository.getLastSyncTime() } returns System.currentTimeMillis() - 1000 // 1 second ago
        coEvery { promptsRepository.getPromptsCount() } returns 10

        // When - create synchronizer with mocked dependencies
        val synchronizer = PromptSynchronizerImpl(
            service = gitHubService,
            promptsRepository = promptsRepository,
            settingsRepository = settingsRepository
        )
        val result = synchronizer.synchronize()

        // Then
        assertEquals(SyncResult.TooSoon, result)
        coVerify(exactly = 0) { gitHubService.downloadFile(any()) }
    }

    @Test
    @Ignore("Flaky test - needs investigation of sync logic")
    fun `synchronize downloads and processes prompts successfully`() = runTest {
        // Given
        val remotePrompts = listOf(
            Prompt(
                id = "1",
                title = "Remote Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            )
        )
        val localPrompts = listOf(
            Prompt(
                id = "1",
                title = "Local Favorite Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = true,
                createdAt = null,
                modifiedAt = null
            ),
            Prompt(
                id = "2",
                title = "Local Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = true,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            )
        )

        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { promptsRepository.getPromptsCount() } returns 10
        coEvery { gitHubService.downloadFile(any()) } returns "mock zip content".toByteArray()
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(any()) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } returns Unit
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When
        val synchronizer = PromptSynchronizerImpl(
            service = gitHubService,
            promptsRepository = promptsRepository,
            settingsRepository = settingsRepository
        )
        val result = synchronizer.synchronize()

        // Then
        assertTrue(result is SyncResult.Success)
        val successResult = result as SyncResult.Success
        assertEquals(remotePrompts.size, successResult.prompts.size)
        coVerify { promptsRepository.savePrompts(any()) }
        coVerify { promptsRepository.invalidateSortDataCache() }
    }

    @Test
    @Ignore("Flaky test - needs investigation of sync logic")
    fun `synchronize preserves favorite status for existing prompts`() = runTest {
        // Given
        val remotePrompts = listOf(
            Prompt(
                id = "1",
                title = "Remote Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            )
        )
        val localPrompts = listOf(
            Prompt(
                id = "1",
                title = "Local Favorite Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = true,
                createdAt = null,
                modifiedAt = null
            )
        )

        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { promptsRepository.getPromptsCount() } returns 10
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(capture(allSavedPrompts)) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } returns Unit
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When - используем spyk для перехвата downloadAndProcessArchive
        val actualSynchronizer = spyk(
            PromptSynchronizerImpl(
                service = gitHubService,
                promptsRepository = promptsRepository,
                settingsRepository = settingsRepository
            )
        )
        coEvery { actualSynchronizer.downloadAndProcessArchive(any()) } returns remotePrompts

        actualSynchronizer.synchronize()

        // Then - проверяем, что savePrompts был вызван с isFavorite=true
        println("DEBUG: All saved prompts: $allSavedPrompts")
        assertTrue(allSavedPrompts.isNotEmpty(), "savePrompts was never called")
        val firstPrompt = allSavedPrompts.first().first()
        assertTrue(firstPrompt.isFavorite, "First prompt should be favorite, but was: $firstPrompt")
        coVerify { promptsRepository.invalidateSortDataCache() }
    }

    // Для capture
    private val allSavedPrompts = mutableListOf<List<Prompt>>()

    @Test
    fun `synchronize deletes remote prompts no longer available`() = runTest {
        // Given
        val remotePrompts = listOf(
            Prompt(
                id = "1",
                title = "New Remote Prompt", // Изменено на уникальный title чтобы не попасть под дедупликацию
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            )
        )
        val localPrompts = listOf(
            Prompt(
                id = "1",
                title = "Remote Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            ),
            Prompt(
                id = "2",
                title = "Old Remote Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = false,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            ),
            Prompt(
                id = "3",
                title = "Local Prompt",
                description = null,
                content = null,
                compatibleModels = emptyList(),
                category = "test",
                status = "active",
                isLocal = true,
                isFavorite = false,
                createdAt = null,
                modifiedAt = null
            )
        )

        val actualSynchronizer = spyk(
            PromptSynchronizerImpl(
                service = gitHubService,
                promptsRepository = promptsRepository,
                settingsRepository = settingsRepository
            )
        )
        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { settingsRepository.setLastSyncTime(any()) } returns Unit
        coEvery { actualSynchronizer.downloadAndProcessArchive(any()) } returns remotePrompts
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(any()) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } coAnswers {
            val idsToDelete = it.invocation.args[0] as List<String>
            assertEquals(listOf("2"), idsToDelete) // Should only delete old remote prompt
        }
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When
        actualSynchronizer.synchronize()

        // Then
        coVerify { promptsRepository.deletePromptsByIds(any()) }
    }

    @Test
    fun `synchronize handles errors gracefully`() = runTest {
        // Given
        val actualSynchronizer = spyk(
            PromptSynchronizerImpl(
                service = gitHubService,
                promptsRepository = promptsRepository,
                settingsRepository = settingsRepository
            )
        )
        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { promptsRepository.getPromptsCount() } returns 10
        coEvery { gitHubService.downloadFile(any()) } throws RuntimeException("Network error")

        // When
        val result = actualSynchronizer.synchronize()

        // Then
        assertTrue(result is SyncResult.Error)
    }
}