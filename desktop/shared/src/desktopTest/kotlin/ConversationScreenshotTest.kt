@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.time.ExperimentalTime::class)

import androidx.compose.ui.ImageComposeScene
import com.arny.aiprompts.data.model.*
import com.arny.aiprompts.presentation.features.llm.*
import com.arny.aiprompts.presentation.ui.llm.LlmScreen
import com.arny.aiprompts.results.DataResult
import com.arny.sharedui.PromptTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class ConversationScreenshotTest {
    @Test fun conversationAtThreeWidths() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val session = ChatSession("one", "План приложения", "Отвечай кратко", ChatSettings(), 0, 0)
        val state = LlmUiState(chatSessions = listOf(session), selectedChatId = session.id,
            modelsResult = DataResult.Success(listOf(LlmModel("test-model", "Тестовая модель", "", 0, 32000, null, null, null, listOf("text"), listOf("text"), true))),
            prompt = "Продолжи план", messages = listOf(
                ChatMessage(role = ChatMessageRole.USER, content = "Как объединить Android и desktop?", timestamp = 1),
                ChatMessage(role = ChatMessageRole.MODEL, content = "Используй **общий UI** и адаптивный layout.\n\n1. Компактное окно — одна панель.\n2. Широкое окно — список и содержимое рядом.\n\n```kotlin\nval compact = width < 600.dp\n```", timestamp = 2, modelId = "test-model")))
        val component = mockk<LlmComponent>(relaxed = true) { every { uiState } returns MutableStateFlow(state) }
        for (width in listOf(360, 720, 1440)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) { LlmScreen(component) } }
                scene.render(0).close()
                scene.render(1_000_000_000).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "conversation-$width.png").writeBytes(it.bytes) }
                }
            } finally { scene.close() }
        }
    }
}
