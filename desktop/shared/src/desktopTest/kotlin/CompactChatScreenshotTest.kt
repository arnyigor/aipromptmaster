import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.presentation.ui.llm.ModelSelectionDialog
import com.arny.aiprompts.presentation.ui.llm.components.ParametersPanel
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.data.model.LlmModel
import java.math.BigDecimal
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
                            LlmUiState(modelsResult = DataResult.Success(listOf(
                                LlmModel("fixture-1", "Модель с длинным названием для проверки компактного окна",
                                    "Текст и изображения; описание переносится без сжатия кнопок.", 0L,
                                    128000L, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                    listOf("text", "image"), listOf("text"), true),
                                LlmModel("fixture-2", "Текстовая модель", "Помощник для работы с промптами", 0L,
                                    32000L, BigDecimal.ZERO, BigDecimal.ZERO, null,
                                    listOf("text"), listOf("text"), false),
                            ))),
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
