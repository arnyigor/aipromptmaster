package com.arny.promptcontract

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ModelAvailabilityTest {
    private class Store(var profile: ProviderProfile = ProviderProfile("local", "Local", "http://localhost:1234/v1", requiresKey = false)) : ProviderStore {
        override fun loadProviders() = ProviderConfig(listOf(profile), profile.id)
        override fun saveProviders(config: ProviderConfig) { error("Diagnostic must not save settings") }
    }
    private class Probe(private val action: suspend (ProviderProfile, String) -> Unit) : ProviderProbe {
        override suspend fun models(profile: ProviderProfile) = error("Model list does not verify access")
        override suspend fun checkModel(profile: ProviderProfile, modelId: String) = action(profile, modelId)
    }

    @Test fun explicitCheckUsesActiveProfileAndSelectedModelWithoutChangingSettings() = runTest {
        val store = Store()
        var count = 0
        val controller = ModelAvailabilityController(store, Probe { profile, model ->
            assertEquals("local", profile.id); assertEquals("chosen/model", model); count++
        }, this)
        assertEquals(0, count)
        controller.check("chosen/model"); advanceUntilIdle()
        assertEquals(1, count)
        assertEquals(true, controller.state.value.available)
        assertEquals("chosen/model", controller.state.value.modelId)
        assertEquals("Local", controller.state.value.providerName)
    }

    @Test fun missingRequiredKeyPreventsNetworkRequest() = runTest {
        val controller = ModelAvailabilityController(Store(ProviderProfile.openRouter()), Probe { _, _ -> error("Must not send") }, this)
        controller.check("model"); advanceUntilIdle()
        assertEquals(false, controller.state.value.available)
        assertContains(controller.state.value.message.orEmpty(), "API-ключ")
    }

    @Test fun statusCodesRemainDistinctAndNeverEchoServerSecrets() = runTest {
        for (status in listOf(400, 401, 402, 403, 404, 422, 429, 503)) {
            val controller = ModelAvailabilityController(Store(), Probe { _, _ -> throw ModelProbeHttpError(status) }, this)
            controller.check("model"); advanceUntilIdle()
            assertEquals(false, controller.state.value.available)
            assertContains(controller.state.value.message.orEmpty(), status.toString())
        }
        val controller = ModelAvailabilityController(Store(), Probe { _, _ -> error("SECRET-API-KEY raw response") }, this)
        controller.check("model"); advanceUntilIdle()
        assertFalse(controller.state.value.message.orEmpty().contains("SECRET"))
    }

    @Test fun duplicateClickIsIgnoredAndResetCancelsPendingRequestAndStaleResult() = runTest {
        var calls = 0
        var cancelled = false
        val controller = ModelAvailabilityController(Store(), Probe { _, _ ->
            calls++
            try { awaitCancellation() } finally { cancelled = true }
        }, this)
        controller.check("first"); controller.check("first"); runCurrent()
        assertEquals(1, calls)
        controller.reset(); runCurrent()
        assertTrue(cancelled)
        assertEquals(ModelAvailabilityUi(), controller.state.value)
    }

    @Test fun timeoutStopsCheckingWithoutReportingAvailability() = runTest {
        val controller = ModelAvailabilityController(Store(), Probe { _, _ -> awaitCancellation() }, this, timeoutMillis = 100)
        controller.check("model"); advanceUntilIdle()
        assertFalse(controller.state.value.checking)
        assertEquals(false, controller.state.value.available)
        assertContains(controller.state.value.message.orEmpty(), "время")
    }

    @Test fun requestHasNoHistoryAndAcceptsOnlyCompletionResponses() {
        val body = Json.parseToJsonElement(modelProbeBody("chosen/model")).jsonObject
        assertEquals(16, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(false, body["stream"]!!.jsonPrimitive.boolean)
        assertEquals(1, body["messages"]!!.jsonArray.size)
        assertEquals("chosen/model", body["model"]!!.jsonPrimitive.content)
        for (url in listOf("https://api.openai.com/v1", "https://openrouter.ai/api/v1/")) {
            val modern = Json.parseToJsonElement(modelProbeBody("chosen/model", url)).jsonObject
            assertEquals(16, modern["max_completion_tokens"]!!.jsonPrimitive.int)
            assertFalse(modern.containsKey("max_tokens"))
        }
        verifyModelProbeResponse("""{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}""")
        verifyModelProbeResponse("""{"choices":[{"message":{"content":null,"reasoning":"OK"},"finish_reason":"length"}]}""")
        for (bodyText in listOf("{}", """{"data":[{"id":"model"}]}""", """{"error":{"message":"secret"},"choices":[]}"""))
            assertFailsWith<ModelProbeResponseError> { verifyModelProbeResponse(bodyText) }
    }
    @Test fun resultIsRetainedOnlyForTheSameModelAndProviderProfile() = runTest {
        val store = Store()
        val controller = ModelAvailabilityController(store, Probe { _, _ -> }, this)
        controller.check("one"); advanceUntilIdle()
        controller.retainFor("one")
        assertEquals(true, controller.state.value.available)
        store.profile = store.profile.copy(name = "Changed provider")
        controller.retainFor("one")
        assertEquals(ModelAvailabilityUi(), controller.state.value)
        controller.check("one"); advanceUntilIdle()
        controller.retainFor("two")
        assertEquals(ModelAvailabilityUi(), controller.state.value)
    }
}
