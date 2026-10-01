import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.presentation.ui.llm.ModelSelectionDialog
import com.arny.aiprompts.presentation.ui.llm.components.ParametersPanel
import com.arny.aiprompts.results.DataResult
import com.arny.sharedui.PromptTheme
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalComposeUiApi::class)
class CompactChatScreenshotTest {
    @Test fun renderCompactModelSelectionAndParameters() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        for (width in listOf(360, 480, 720)) {
            for (dialog in listOf(true, false)) {
                val scene = ImageComposeScene(width, 900)
                try {
                    scene.setContent { PromptTheme(dark = false) {
                        if (dialog) ModelSelectionDialog(
                            LlmUiState(modelsResult = DataResult.Success(emptyList())),
                            {}, {}, {}, {}, {}, {}
                        ) else ParametersPanel(null, null, {}, {}, onDismiss = {})
                    } }
                    scene.render(0).close()
                    scene.render(1_000_000_000).use { rendered ->
                        rendered.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(output, "chat-${if (dialog) "models" else "parameters"}-$width.png").writeBytes(data.bytes)
                        }
                    }
                } finally { scene.close() }
            }
        }
    }
}
