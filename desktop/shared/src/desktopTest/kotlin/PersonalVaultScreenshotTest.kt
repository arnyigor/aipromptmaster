import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.presentation.screens.*
import com.arny.aiprompts.presentation.ui.settings.SettingsScreen
import com.arny.promptcontract.*
import com.arny.sharedui.PromptTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class PersonalVaultScreenshotTest {
    @Test fun settingsGithubFitsCompactAndWideWindows() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val component = mockk<SettingsComponent>(relaxed = true)
        every { component.state } returns MutableStateFlow(SettingsState(activeSection = SettingsSection.GITHUB))
        every { component.providers } returns MutableStateFlow(ProviderManagerState())
        every { component.personalVault } returns MutableStateFlow(PersonalVaultUi(config = PersonalVaultConfig("owner/personal-prompts", "main", "fake-only-screenshot-token")))
        for (width in listOf(360, 760, 1440)) {
            val scene = ImageComposeScene(width, 1000)
            try {
                scene.setContent { PromptTheme(dark = false) { SettingsScreen(component) } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "personal-github-$width.png").writeBytes(it.bytes) }
                }
            } finally { scene.close() }
        }
    }
}
