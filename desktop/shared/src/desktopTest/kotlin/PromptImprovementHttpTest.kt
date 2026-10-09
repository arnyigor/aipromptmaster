@file:OptIn(kotlin.time.ExperimentalTime::class)

import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.domain.files.FilePromptProcessor
import com.arny.aiprompts.domain.usecase.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class PromptImprovementHttpTest {
    @Test fun `real HTTP supports streaming and regular requests without local API key`() = runBlocking {
        val requests = mutableListOf<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val request = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
            requests.add(request)
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            val streaming = request["stream"]?.jsonPrimitive?.booleanOrNull == true
            val body = if (streaming) "data: {\"choices\":[{\"delta\":{\"content\":\"Improved {name}\"}}]}\n\ndata: [DONE]\n\n" else "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Improved {name}\"},\"finish_reason\":\"stop\"}]}"
            exchange.responseHeaders.add("Content-Type", if (streaming) "text/event-stream" else "application/json")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val serializer = Json { ignoreUnknownKeys = true }
        val client = HttpClient {
            install(ContentNegotiation) { json(serializer) }
            install(HttpTimeout)
        }
        try {
            val settings = mockk<ISettingsRepository> {
                every { loadProviders() } returns com.arny.promptcontract.ProviderConfig(
                    profiles = listOf(com.arny.promptcontract.ProviderProfile.openRouter(),
                        com.arny.promptcontract.ProviderProfile("local", "Local", "http://127.0.0.1:${server.address.port}/v1", requiresKey = false)), activeId = "local")
                every { getBaseUrl() } returns "http://127.0.0.1:${server.address.port}/v1"
                every { getOpenRouterApiKey() } returns null
            }
            val repository = OpenRouterRepositoryImpl(client, serializer, settings, mockk<FilePromptProcessor>())
            val useCase = ImprovePromptUseCase(repository, settings)
            for (stream in listOf(true, false)) {
                assertEquals("Improved {name}", useCase(PromptImprovementRequest("Original {name}", "fixture-model", temperature = 0.3, maxTokens = 2048, stream = stream)).toList().last())
            }
            assertEquals(2, requests.size)
            assertEquals(true, requests.first()["stream"]?.jsonPrimitive?.boolean)
            requests.forEach { request ->
                assertEquals("fixture-model", request["model"]?.jsonPrimitive?.content)
                assertEquals(0.3, request["temperature"]?.jsonPrimitive?.double)
                assertEquals(2048, request["max_tokens"]?.jsonPrimitive?.int)
                assertEquals("Original {name}", request["messages"]?.jsonArray?.last()?.jsonObject?.get("content")?.jsonPrimitive?.content)
                request["messages"]!!.jsonArray.forEach { assertEquals(setOf("role", "content"), it.jsonObject.keys) }
            }
        } finally {
            client.close()
            server.stop(0)
        }
    }
    @Test fun nullContentAndTokenLimitReturnAnExplainedFailureWithoutDeserializationCrash() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.requestBody.readBytes()
            val bytes = """{"choices":[{"message":{"role":"assistant","content":null},"finish_reason":"length"}]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val serializer = Json { ignoreUnknownKeys = true }
        val client = HttpClient { install(ContentNegotiation) { json(serializer) } }
        try {
            val url = "http://127.0.0.1:${server.address.port}/v1"
            val settings = mockk<ISettingsRepository> {
                every { loadProviders() } returns com.arny.promptcontract.ProviderConfig(
                    listOf(com.arny.promptcontract.ProviderProfile("local", "Local", url, requiresKey=false)), "local")
            }
            val repository = OpenRouterRepositoryImpl(client, serializer, settings, mockk<FilePromptProcessor>())
            val failure = repository.getChatCompletion("model",emptyList()).exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals("Ответ обрезан лимитом токенов", failure.message)
        } finally { client.close(); server.stop(0) }
    }

}
