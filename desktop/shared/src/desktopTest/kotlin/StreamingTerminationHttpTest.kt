import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.data.model.*
import com.arny.promptcontract.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import kotlin.test.*
class StreamingTerminationHttpTest {
 @Test fun realHttpDistinguishesSuccessfulCompletionFromEofAndProviderError() = runBlocking {
  var response=""
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.createContext("/v1/chat/completions") { e ->
   e.requestBody.readBytes(); e.responseHeaders.add("Content-Type","text/event-stream")
   val bytes=response.toByteArray(); e.sendResponseHeaders(200,bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
  }
  server.start()
  val serializer=Json { ignoreUnknownKeys=true }
  val client=HttpClient { install(ContentNegotiation) { json(serializer) } }
  try {
   val url="http://127.0.0.1:${server.address.port}/v1"
   val settings=mockk<ISettingsRepository> {
    every { loadProviders() } returns ProviderConfig(listOf(ProviderProfile("local","Local",url,requiresKey=false)),"local")
    every { getBaseUrl() } returns url
    every { getOpenRouterApiKey() } returns null
   }
   val repo=OpenRouterRepositoryImpl(client,serializer,settings,mockk())
   val chunk="data:{\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n"
   for ((ending,success) in listOf("data:[DONE]\n\n" to true,"" to false,"data:{\"error\":{\"message\":\"failure\"}}\n\n" to false)) {
    response=": heartbeat\n\n"+chunk+ending
    val results=repo.getStreamingChatCompletion("model",emptyList()).toList()
    assertTrue(results.any { it.getOrNull()?.content=="Partial" })
    assertEquals(success,results.all { it.isSuccess })
   }
  } finally { client.close(); server.stop(0) }
 }
}
