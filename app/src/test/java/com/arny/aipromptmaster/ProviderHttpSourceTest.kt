package com.arny.aipromptmaster

import com.arny.aipromptmaster.data.*
import com.arny.aipromptmaster.domain.*
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.promptcontract.*
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Test
import kotlin.test.*

class ProviderHttpSourceTest {
    @Test fun `real provider HTTP checks models parameters streaming and isolated keys`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"fixture-model\"}]}"))
        server.enqueue(MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Better {name}\"}}]}\n\ndata: [DONE]\n\n"))
        server.enqueue(MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"Better {name}\"},\"finish_reason\":\"stop\"}]}"))
        server.start()
        val client = OkHttpClient()
        try {
            val profile = ProviderProfile("fixture", "Fixture", server.url("/v1").toString(), requiresKey = false)
            val settings = mockk<ISettingsRepository> { every { loadProviders() } returns ProviderConfig(listOf(ProviderProfile.openRouter("unused-key"), profile), profile.id) }
            val gateway = ProviderHttpSource(client, Json { ignoreUnknownKeys = true })
            assertEquals(listOf("fixture-model"), gateway.models(profile))
            assertEquals("/v1/models", server.takeRequest().path)
            val useCase = ImprovePromptUseCase(gateway, settings)
            for (stream in listOf(true, false)) {
                assertEquals("Better {name}", useCase(ImprovementRequest("Original {name}", "fixture-model", temperature = 0.2, maxTokens = 512, stream = stream)).toList().last())
                val recorded = server.takeRequest()
                assertNull(recorded.getHeader("Authorization"))
                val request = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
                assertEquals(512, request["max_tokens"]?.jsonPrimitive?.int)
                assertEquals(0.2, request["temperature"]?.jsonPrimitive?.double)
                assertEquals("Original {name}", request["messages"]?.jsonArray?.last()?.jsonObject?.get("content")?.jsonPrimitive?.content)
            }
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun `chat routes to active endpoint with only its own key`() {
        val server = MockWebServer(); server.enqueue(MockResponse().setBody("{}")); server.start()
        val local = ProviderProfile("local", "Local", server.url("/v1").toString().trimEnd('/'), "fixture-key")
        val settings = mockk<ISettingsRepository> { every { loadProviders() } returns ProviderConfig(listOf(ProviderProfile.openRouter("unused-router-key"), local), "local") }
        val client = OkHttpClient.Builder().addInterceptor(ProviderRoutingInterceptor(settings)).build()
        try {
            client.newCall(Request.Builder().url("https://openrouter.ai/api/v1/models").header("Authorization", "Bearer unused-router-key").build()).execute().close()
            val recorded = server.takeRequest()
            assertEquals("/v1/models", recorded.path)
            assertEquals("Bearer fixture-key", recorded.getHeader("Authorization"))
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
