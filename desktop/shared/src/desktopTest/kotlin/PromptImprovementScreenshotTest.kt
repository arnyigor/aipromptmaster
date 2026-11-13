import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import com.arny.aiprompts.presentation.ui.detail.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test

/** Opt-in screenshots of the real composable, rendered without touching desktop windows. */
@OptIn(ExperimentalComposeUiApi::class)
class PromptImprovementScreenshotTest {
    @Test fun `capture real dialog preview`() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        File(output).mkdirs()
        for (width in listOf(1100, 480)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent {
                    MaterialTheme {
                        PromptImprovementDialog(PromptImprovementState(
                            visible = true, model = "fixture-model", source = "Напиши описание продукта для {audience}.",
                            result = "Создай ясное описание продукта для {audience}. Укажи преимущества и заверши призывом к действию.",
                            complete = true, firstResponseMs = 120, elapsedMs = 850
                        )) {}
                    }
                }
                scene.render(0).close()
                scene.render(1_000_000_000).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(output, "prompt-improvement-$width.png").writeBytes(data.bytes)
                    }
                }
            } finally { scene.close() }
        }
    }
}
