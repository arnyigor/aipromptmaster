import com.arny.aiprompts.data.repositories.ProviderHttpProbe
import com.arny.promptcontract.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

/** Explicit opt-in: one tiny generation, a free model, no saved credentials/history modified. */
class ConfiguredModelAvailabilityTest {
    @Test fun realOpenRouterCheckReturnsAnAvailabilityResult() = runBlocking {
        assumeTrue(System.getenv("CHECK_MODEL_AVAILABILITY") == "1" && !System.getenv("OPENROUTER_API_KEY").isNullOrBlank())
        val profile = ProviderProfile.openRouter().copy(keySource = ProviderKeySource.ENVIRONMENT)
        val client = HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 30_000 }
        }
        try {
            val probe = ProviderHttpProbe(client)
            val models = probe.models(profile)
            val model = models.firstOrNull { it == "openrouter/free" }
                ?: models.firstOrNull { it.endsWith(":free") } ?: error("No free model for this diagnostic")
            val store = object : ProviderStore {
                override fun loadProviders() = ProviderConfig(listOf(profile))
                override fun saveProviders(config: ProviderConfig) { error("Diagnostic must not save settings") }
                override fun environmentKeyAvailable() = true
            }
            val controller = ModelAvailabilityController(store, probe, this)
            controller.check(model)
            val result = withTimeout(35_000) { controller.state.first { it.available != null && !it.checking } }
            assertEquals(model, result.modelId)
            assertNotNull(result.available)
            assertTrue(result.message.orEmpty().isNotBlank())
            println("Real model availability check: model=$model; available=${result.available}; ${result.message}")
        } finally { client.close() }
    }
}
