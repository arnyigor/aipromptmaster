package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.db.entities.ModelEntity
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.promptcontract.ProviderConfig
import com.arny.promptcontract.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Список моделей объединяет провайдеров и фильтруется по выбранному. */
class ModelProviderFilterTest {

    private val settings = mockk<ISettingsRepository>(relaxed = true)
    private val dao = mockk<ModelDao>(relaxed = true)
    private val service = mockk<OpenRouterService>(relaxed = true)
    private val syncMetadata = mockk<SyncMetadata>(relaxed = true)

    private val openRouter = ProviderProfile.openRouter("k")
    private val local = ProviderProfile("local", "llama.cpp", "http://127.0.0.1:8081/v1", requiresKey = false)

    private fun model(id: String, providerId: String) = ModelEntity(
        id = id, providerId = providerId, name = id, description = "",
        contextLength = 0, pricingPrompt = "0", pricingCompletion = "0", pricingImage = null,
        inputModalities = "[]", outputModalities = "[]", isMultimodal = false,
        isFavorite = false, isFree = true, isSelected = false, sortPriority = 0, lastUpdated = 0,
    )

    private fun repository() = ModelRepositoryImpl(service, dao, syncMetadata, settings)

    @Test
    fun `all providers are returned with their names`() = runTest {
        every { settings.observeProviders() } returns flowOf(ProviderConfig(listOf(openRouter, local), activeId = openRouter.id))
        every { dao.getModels(any(), any(), any(), any(), any()) } returns
            flowOf(listOf(model("openrouter/free", "openrouter"), model("qwen", local.id)))

        val models = repository().getModels(ModelsFilter()).first()

        assertEquals(2, models.size)
        assertEquals("llama.cpp", models.first { it.providerId == local.id }.providerName)
        assertEquals("OpenRouter", models.first { it.providerId == "openrouter" }.providerName)
    }

    @Test
    fun `provider filter keeps only that provider`() = runTest {
        every { settings.observeProviders() } returns flowOf(ProviderConfig(listOf(openRouter, local), activeId = openRouter.id))
        every { dao.getModels(any(), any(), any(), any(), any()) } returns
            flowOf(listOf(model("openrouter/free", "openrouter"), model("qwen", local.id)))

        val models = repository().getModels(ModelsFilter(providerId = local.id)).first()

        assertEquals(listOf("qwen"), models.map { it.id })
    }

    @Test
    fun `unknown provider is rejected instead of silently switching`() = runTest {
        every { settings.loadProviders() } returns ProviderConfig(listOf(openRouter, local), activeId = openRouter.id)
        val saved = io.mockk.slot<ProviderConfig>()
        every { settings.saveProviders(capture(saved)) } returns Unit

        val error = runCatching { repository().selectModel("deleted-profile", "some-model") }.exceptionOrNull()

        org.junit.Assert.assertNotNull(error)
        org.junit.Assert.assertEquals("openRouter must stay active", openRouter.id, settings.loadProviders().activeId)
    }

    @Test
    fun `custom provider models are not reported as free`() = runTest {
        val gateway = io.mockk.mockk<com.arny.aipromptmaster.domain.ProviderGateway>(relaxed = true)
        io.mockk.coEvery { gateway.models(any()) } returns listOf("model-x")
        every { settings.loadProviders() } returns ProviderConfig(listOf(openRouter, local), activeId = local.id)
        every { settings.observeProviders() } returns flowOf(ProviderConfig(listOf(openRouter, local), activeId = local.id))
        every { dao.getModels(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        val repo = ModelRepositoryImpl(service, dao, syncMetadata, settings, gateway)
        repo.refreshModels()

        val saved = io.mockk.slot<List<ModelEntity>>()
        io.mockk.coVerify { dao.syncModels(capture(saved)) }
        org.junit.Assert.assertFalse(saved.captured.first().isFree)
        org.junit.Assert.assertEquals("unknown", saved.captured.first().pricingPrompt)
    }
}
