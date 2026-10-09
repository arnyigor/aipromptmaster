import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.ModelAvailabilityUi
import com.arny.sharedui.ModelAvailabilityControl
import com.arny.sharedui.PromptTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class ModelAvailabilityScreenshotTest {
    @Test fun checkingSuccessAndFailureFitCompactWithLargeText() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val states = listOf(ModelAvailabilityUi(checking = true),
            ModelAvailabilityUi(available = true, message = "Модель доступна · OpenRouter"),
            ModelAvailabilityUi(available = false, message = "Нет доступа к модели (403)"))
        for ((index, state) in states.withIndex()) for (dark in listOf(false, true)) {
            val scene = ImageComposeScene(320, 260, density = Density(1f, 1.3f))
            try {
                scene.setContent { PromptTheme(dark = dark) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(12.dp)) {
                        androidx.compose.material3.OutlinedTextField("provider/selected-model", {}, label = { androidx.compose.material3.Text("Модель по умолчанию (ID)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        ModelAvailabilityControl("provider/selected-model", state, {})
                    }
                } } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "model-check-$index-${if (dark) "dark" else "light"}-320.png").writeBytes(it.bytes)
                } }
            } finally { scene.close() }
        }
    }
}
