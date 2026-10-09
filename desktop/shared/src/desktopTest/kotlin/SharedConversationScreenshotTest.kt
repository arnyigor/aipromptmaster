import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.presentation.ui.llm.components.ChatSidebar
import com.arny.aiprompts.presentation.ui.llm.components.ParametersPanel
import com.arny.sharedui.PromptTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class SharedConversationScreenshotTest {
    @Test fun renderPopulatedHistoryAndParameters() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val sessions = listOf(
            ChatSession(id = "fixture-1", name = "План объединения Android и Desktop с длинным названием",
                systemPrompt = "Отвечай по-русски. Сохраняй переменные промптов и объясняй следующие действия.", settings = ChatSettings(), createdAt = 0, updatedAt = 0),
            ChatSession(id = "fixture-2", name = "Редактирование промпта", systemPrompt = null, settings = ChatSettings(), createdAt = 0, updatedAt = 0),
        )
        for (width in listOf(360, 720)) for (parameters in listOf(false, true)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) {
                    if (parameters) ParametersPanel(sessions[0], null, {}, {}, onDismiss = {})
                    else ChatSidebar(sessions, "fixture-1", {}, {}, {}, { _, _ -> }, {})
                } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(output, "shared-chat-${if (parameters) "parameters" else "history"}-$width.png").writeBytes(it.bytes)
                    }
                }
            } finally { scene.close() }
        }
    }
}
