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
}
