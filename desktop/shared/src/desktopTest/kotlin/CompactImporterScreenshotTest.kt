import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.presentation.ui.importer.ImporterComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterState
import com.arny.aiprompts.presentation.ui.importer.ImporterScreen
import com.arny.sharedui.PromptTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class CompactImporterScreenshotTest {
    @Test fun renderImporterAtThreeWidths() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val component = mockk<ImporterComponent>(relaxed = true)
        every { component.state } returns MutableStateFlow(ImporterState())
        for (width in listOf(360, 720, 1220)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) { ImporterScreen(component) } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(output, "importer-$width.png").writeBytes(it.bytes)
                    }
                }
            } finally { scene.close() }
        }
    }
}
