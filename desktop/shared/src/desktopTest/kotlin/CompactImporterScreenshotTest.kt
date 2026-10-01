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

@OptIn(ExperimentalComposeUiApi::class, kotlin.time.ExperimentalTime::class)
class CompactImporterScreenshotTest {
    @Test fun renderImporterAtThreeWidths() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val component = mockk<ImporterComponent>(relaxed = true)
        val state = MutableStateFlow(ImporterState())
        every { component.state } returns state
        for (width in listOf(360, 720, 1220)) {
            for (pane in listOf(-1, 0, 1, 2)) {
            state.value = if (pane < 0) ImporterState() else {
                val post = com.arny.aiprompts.domain.model.RawPostData("fixture", com.arny.aiprompts.domain.model.Author("one", "Автор"),
                    kotlin.time.Instant.fromEpochMilliseconds(1), fullHtmlContent = "Помоги составить план приложения", isLikelyPrompt = true)
                ImporterState(rawPosts = listOf(post), selectedPostId = post.postId, postsToImport = setOf(post.postId),
                    activePane = pane, availableCategories = listOf("general", "technology"),
                    editedData = mapOf(post.postId to com.arny.aiprompts.presentation.ui.importer.EditedPostData(
                        title = "План приложения", description = "План разработки Android и desktop", content = "Составь план разработки приложения", category = "technology")))
            }
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) { ImporterScreen(component) } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(output, "importer-$width-${if (pane < 0) "empty" else "pane-$pane"}.png").writeBytes(it.bytes)
                    }
                }
            } finally { scene.close() }
            }
        }
    }
}
