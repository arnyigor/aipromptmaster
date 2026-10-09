import com.arny.aiprompts.data.repositories.ProviderHttpProbe
import com.arny.promptcontract.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ModelAvailabilityHttpTest {
    @Test fun diagnosticUsesCompletionEndpointOwnKeyAndPreservesAccessErrors() = runBlocking {
        val status = AtomicInteger(200)
        val requests = mutableListOf<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            requests.add(Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject)
            assertEquals("Bearer fixture-only-key", exchange.requestHeaders.getFirst("Authorization"))
            val bytes = (if (status.get() == 200) """{"choices":[{"message":{"content":"OK"}}]}""" else "secret response body").toByteArray()
            exchange.sendResponseHeaders(status.get(), bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = HttpClient()
        try {
            val profile = ProviderProfile("fixture", "Fixture", "http://127.0.0.1:${server.address.port}/v1", "fixture-only-key")
            val probe = ProviderHttpProbe(client)
            probe.checkModel(profile, "chosen/model")
            assertEquals("chosen/model", requests.first()["model"]!!.jsonPrimitive.content)
            assertEquals(16, requests.first()["max_tokens"]!!.jsonPrimitive.int)
            assertEquals(false, requests.first()["stream"]!!.jsonPrimitive.boolean)
            assertEquals(1, requests.first()["messages"]!!.jsonArray.size)
            for (code in listOf(401, 402, 403, 404, 429, 503)) {
                status.set(code)
                val error = assertFailsWith<ModelProbeHttpError> { probe.checkModel(profile, "chosen/model") }
                assertEquals(code, error.status)
                assertFalse(error.message.orEmpty().contains("secret"))
            }
        } finally { client.close(); server.stop(0) }
    }
}
