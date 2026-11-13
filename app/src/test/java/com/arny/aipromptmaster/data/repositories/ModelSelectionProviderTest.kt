package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.promptcontract.ProviderConfig
import com.arny.promptcontract.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Выбор модели не должен ограничиваться активным профилем: модель тянет за собой своего провайдера. */
class ModelSelectionProviderTest {

    private val settings = mockk<ISettingsRepository>(relaxed = true)
    private val dao = mockk<ModelDao>(relaxed = true)
    private val service = mockk<OpenRouterService>(relaxed = true)
    private val syncMetadata = mockk<SyncMetadata>(relaxed = true)

    private fun repository() = ModelRepositoryImpl(service, dao, syncMetadata, settings)

    private val openRouter = ProviderProfile.openRouter("k")
    private val local = ProviderProfile("local-llama", "llama.cpp", "http://127.0.0.1:8081/v1", requiresKey = false)

    @Test
    fun `selecting a model of another provider switches the active profile`() = runTest {
        every { settings.loadProviders() } returns ProviderConfig(listOf(openRouter, local), activeId = openRouter.id)
        val saved = slot<ProviderConfig>()
        every { settings.saveProviders(capture(saved)) } returns Unit

        repository().selectModel(providerId = local.id, modelId = "qwen-local")

        assertEquals(local.id, saved.captured.activeId)
        assertEquals("qwen-local", saved.captured.profiles.first { it.id == local.id }.modelId)
        assertEquals("", saved.captured.profiles.first { it.id == openRouter.id }.modelId)
    }

    @Test
    fun `legacy call without provider keeps the active profile`() = runTest {
        every { settings.loadProviders() } returns ProviderConfig(listOf(openRouter, local), activeId = openRouter.id)
        val saved = slot<ProviderConfig>()
        every { settings.saveProviders(capture(saved)) } returns Unit

        repository().selectModel("openrouter/free")

        assertEquals(openRouter.id, saved.captured.activeId)
        assertEquals("openrouter/free", saved.captured.profiles.first { it.id == openRouter.id }.modelId)
        coVerify { dao.selectModel(openRouter.id, "openrouter/free") }
    }
}
