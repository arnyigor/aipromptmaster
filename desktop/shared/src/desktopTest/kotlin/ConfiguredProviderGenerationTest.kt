@file:OptIn(kotlin.time.ExperimentalTime::class)

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.repositories.*
import com.arny.aiprompts.di.SettingsFactory
import com.arny.aiprompts.domain.files.FilePromptProcessor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/** Explicit opt-in: two tiny external requests; credentials and response bodies are never printed. */
class ConfiguredProviderGenerationTest {
    @Test fun configuredProviderGeneratesAndStreams() = runBlocking {
        assumeTrue(System.getenv("CHECK_CONFIGURED_GENERATION") == "1")
        val storedSettings = SettingsRepositoryImpl(SettingsFactory())
        val storedConfig = storedSettings.loadProviders()
        val testConfig = if (storedConfig.active.id == "openrouter" && !System.getenv("OPENROUTER_API_KEY").isNullOrBlank())
            storedConfig.copy(profiles = storedConfig.profiles.map {
                if (it.id == "openrouter") it.copy(keySource = com.arny.promptcontract.ProviderKeySource.ENVIRONMENT) else it
            }) else storedConfig
        val settings = object : ISettingsRepository by storedSettings {
            override fun loadProviders() = testConfig
            override fun getOpenRouterApiKey(): String? = com.arny.aiprompts.platform.resolveProviderKey(testConfig.active)
        }
        val profile = settings.loadProviders().active
        assertTrue(!profile.requiresKey || !settings.getOpenRouterApiKey().isNullOrBlank(), "Configured provider requires a key")
        val serializer = Json { ignoreUnknownKeys = true }
        val client = HttpClient(CIO) {
            followRedirects = false
            install(ContentNegotiation) { json(serializer) }
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }
        try {
            val available = ProviderHttpProbe(client).models(profile)
            val model = profile.modelId.takeIf { it.isNotBlank() && it in available }
                ?: available.firstOrNull { it == "openrouter/free" }
                ?: available.firstOrNull { it.endsWith(":free") }
                ?: available.firstOrNull { it.contains("mini") || it.contains("flash") }
                ?: available.first()
            val repository = OpenRouterRepositoryImpl(client, serializer, settings, mockk<FilePromptProcessor>())
            val messages = listOf(ChatMessage(role = ChatMessageRole.USER, content = "Reply with exactly: CONNECTION_OK"))
            val regular = repository.getChatCompletion(model, messages, temperature = 0.0, maxTokens = 512)
                .getOrElse { failure -> throw AssertionError("Real generation failed: ${failure::class.simpleName}; HTTP=${(failure as? com.arny.aiprompts.data.model.ApiException.HttpError)?.code}; model=$model; credentials/body omitted") }
            assertTrue(regular.choices.orEmpty().any { it.message?.content?.isNotBlank() == true }, "Generation returned no text")
            var characters = 0
            var chunks = 0
            var complete = false
            withTimeout(120_000) {
                repository.getStreamingChatCompletion(model, messages, temperature = 0.0, maxTokens = 512).collect { result ->
                    val chunk = result.getOrElse { throw AssertionError("Real streaming failed; credentials/body omitted") }
                    characters += chunk.content.length
                    if (chunk.content.isNotEmpty()) chunks++
                    complete = complete || chunk.isComplete
                }
            }
            assertTrue(characters > 0 && chunks > 0, "Stream returned no text")
            assertTrue(complete, "Stream did not complete")
            println("Real provider generation OK; model=$model; stream text chunks=$chunks; characters=$characters; complete=$complete")
        } finally { client.close() }
    }
}
