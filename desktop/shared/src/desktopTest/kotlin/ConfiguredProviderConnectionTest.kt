import com.arny.aiprompts.data.repositories.ProviderHttpProbe
import com.arny.aiprompts.data.repositories.SettingsRepositoryImpl
import com.arny.aiprompts.di.SettingsFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/** Opt-in read-only check against the existing configured provider; never logs credentials/body. */
class ConfiguredProviderConnectionTest {
    @Test fun configuredProviderListsModels() = runBlocking {
        assumeTrue(System.getenv("CHECK_CONFIGURED_PROVIDER") == "1")
        val profile = SettingsRepositoryImpl(SettingsFactory()).loadProviders().active
        assumeTrue("No configured provider key", !profile.requiresKey || profile.apiKey.isNotBlank())
        val client = HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 }
        }
        try {
            val models = ProviderHttpProbe(client).models(profile)
            assertTrue(models.isNotEmpty(), "Provider returned no models")
            println("Configured provider connection: OK; model count=${models.size}")
        } catch (_: Exception) {
            throw AssertionError("Configured provider connection failed; inspect connection in app settings")
        } finally { client.close() }
    }
}
