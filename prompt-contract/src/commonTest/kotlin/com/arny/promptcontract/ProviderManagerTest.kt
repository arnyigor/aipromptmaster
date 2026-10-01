package com.arny.promptcontract

import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProviderManagerTest {
    private class Store(var config: ProviderConfig = ProviderConfig()) : ProviderStore {
        override fun loadProviders() = config
        override fun saveProviders(config: ProviderConfig) { this.config = config }
    }
    @Test fun `profiles keep separate keys models and active selection`() {
        val router = ProviderProfile.openRouter("router-key").copy(modelId = "router-model")
        val local = ProviderProfile("local", "Local", "http://localhost:1234/v1", "local-key", "local-model", false)
        val config = ProviderConfig(listOf(router)).upsert(local)
        assertEquals("local-key", config.active.apiKey)
        assertEquals("router-key", config.select("openrouter").active.apiKey)
        assertEquals("router-model", config.select("openrouter").active.modelId)
        assertEquals("openrouter", config.delete("local").activeId)
        assertFailsWith<IllegalArgumentException> { config.delete("openrouter") }
        assertFalse(config.toString().contains("router-key"))
    }
    @Test fun `invalid endpoint and duplicate profiles are rejected`() {
        for (url in listOf("ftp://host/v1", "https://user:secret@host/v1", "https://host/v1?key=secret", "")) {
            assertFailsWith<IllegalArgumentException> { ProviderProfile("id", "Name", url, requiresKey = false).validated() }
        }
        assertFailsWith<IllegalArgumentException> { ProviderConfig(listOf(ProviderProfile.openRouter(), ProviderProfile.openRouter())).checked() }
    }
    @Test fun `changing endpoint clears key and does not modify persisted profile until save`() = runTest {
        val store = Store(ProviderConfig(listOf(ProviderProfile.openRouter("original-key"))))
        val manager = ProviderManager(store, ProviderProbe { emptyList() }, this)
        manager.onAction(ProviderAction.Edit("openrouter"))
        manager.onAction(ProviderAction.Url("https://another.example/v1"))
        assertEquals("", manager.state.value.draft?.apiKey)
        assertEquals("original-key", store.config.active.apiKey)
        manager.onAction(ProviderAction.Close)
        assertEquals("https://openrouter.ai/api/v1", store.config.active.baseUrl)
    }
    @Test fun `probe uses draft only and lets user select a returned model`() = runTest {
        val store = Store()
        var probed: ProviderProfile? = null
        val manager = ProviderManager(store, ProviderProbe { probed = it; listOf("b", "a", "a") }, this)
        manager.onAction(ProviderAction.Create(ProviderPreset.LM_STUDIO))
        manager.onAction(ProviderAction.Test); advanceUntilIdle()
        assertEquals(listOf("a", "b"), manager.state.value.models)
        assertEquals("http://localhost:1234/v1", probed?.baseUrl)
        assertEquals("openrouter", store.config.activeId)
        manager.onAction(ProviderAction.Model("a")); manager.onAction(ProviderAction.Save)
        assertEquals("a", store.config.active.modelId)
        assertFalse(store.config.active.requiresKey)
    }
    @Test fun `probe error cannot expose secrets`() = runTest {
        val manager = ProviderManager(Store(), ProviderProbe { error("echoed-secret") }, this)
        manager.onAction(ProviderAction.Create(ProviderPreset.LM_STUDIO))
        manager.onAction(ProviderAction.Test); advanceUntilIdle()
        assertFalse(manager.state.value.message.orEmpty().contains("echoed-secret"))
        assertFalse(manager.state.value.checking)
    }
}
