package com.arny.aiprompts

import com.arny.aiprompts.data.api.GitHubService
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.data.repositories.PromptSynchronizerImpl
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.repositories.SyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptSynchronizerTest {

    private val gitHubService = mockk<GitHubService>()
    private val promptsRepository = mockk<IPromptsRepository>()
    private val settingsRepository = mockk<ISettingsRepository>()

    private val synchronizer = spyk(
        PromptSynchronizerImpl(
            service = gitHubService,
            promptsRepository = promptsRepository,
            settingsRepository = settingsRepository
        )
    )

    @Test
    fun `synchronize returns TooSoon when cooldown active`() = runTest {
        // Given
        coEvery { settingsRepository.getLastSyncTime() } returns System.currentTimeMillis() - 1000 // 1 second ago
        coEvery { settingsRepository.setLastSyncTime(any()) } returns Unit

        // When
        val result = synchronizer.synchronize()

        // Then
        assertEquals(SyncResult.TooSoon, result)
        coVerify(exactly = 0) { gitHubService.downloadFile(any()) }
    }

    @Test
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
        coEvery { settingsRepository.setLastSyncTime(any()) } returns Unit
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns remotePrompts
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(any()) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } returns Unit
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When
        val result = synchronizer.synchronize()

        // Then
        assertTrue(result is SyncResult.Success)
        val successResult = result as SyncResult.Success
        assertEquals(remotePrompts.size, successResult.prompts.size)
        coVerify { promptsRepository.savePrompts(any()) }
        coVerify { promptsRepository.invalidateSortDataCache() }
    }

    @Test
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
        coEvery { settingsRepository.setLastSyncTime(any()) } returns Unit
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns remotePrompts
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(any()) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } returns Unit
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When
        synchronizer.synchronize()

        // Then
        coVerify { promptsRepository.savePrompts(match { it.first().isFavorite }) }
    }

    @Test
    fun `synchronize deletes remote prompts no longer available`() = runTest {
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

        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { settingsRepository.setLastSyncTime(any()) } returns Unit
        coEvery { synchronizer.downloadAndProcessArchive(any()) } returns remotePrompts
        coEvery { promptsRepository.getAllPrompts() } returns flowOf(localPrompts)
        coEvery { promptsRepository.savePrompts(any()) } returns Unit
        coEvery { promptsRepository.deletePromptsByIds(any()) } coAnswers {
            val idsToDelete = it.invocation.args[0] as List<String>
            assertEquals(listOf("2"), idsToDelete) // Should only delete old remote prompt
        }
        coEvery { promptsRepository.invalidateSortDataCache() } returns Unit

        // When
        synchronizer.synchronize()

        // Then
        coVerify { promptsRepository.deletePromptsByIds(any()) }
    }

    @Test
    fun `synchronize handles errors gracefully`() = runTest {
        // Given
        coEvery { settingsRepository.getLastSyncTime() } returns 0L
        coEvery { gitHubService.downloadFile(any()) } throws RuntimeException("Network error")

        // When
        val result = synchronizer.synchronize()

        // Then
        assertTrue(result is SyncResult.Error)
    }
}