import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import com.arny.sharedui.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class AdaptiveShellScreenshotTest {
    @Test fun resizeKeepsContentCompositionAndDraft() {
        val scene = ImageComposeScene(1100, 900)
        var owners = 0
        val observed = mutableSetOf<WindowLayout>()
        try {
            scene.setContent { PromptTheme {
                AdaptiveAppShell(listOf(AppDestination("prompts", "Промпты", Icons.Default.Home)), "prompts", {}) { layout ->
                    val draft = remember { owners++; "Unsaved fixture" }
                    SideEffect { observed.add(layout); assertEquals("Unsaved fixture", draft) }
                    PromptTextEditor("RU", draft, {})
                }
            } }
            for ((index, width) in listOf(1100, 720, 480, 1100).withIndex()) {
                scene.constraints = androidx.compose.ui.unit.Constraints.fixed(width, 900)
                scene.render(index * 1_000_000_000L).close()
                scene.render(index * 1_000_000_000L + 500_000_000L).close()
            }
            assertEquals(setOf(WindowLayout.Compact, WindowLayout.Medium, WindowLayout.Expanded), observed)
            assertEquals(1, owners, "Resize recreated the screen composition")
        } finally { scene.close() }
    }
    @Test fun renderThreeLayouts() {
        assertEquals(WindowLayout.Compact, windowLayout(599f))
        assertEquals(WindowLayout.Medium, windowLayout(600f))
        assertEquals(WindowLayout.Medium, windowLayout(839f))
        assertEquals(WindowLayout.Expanded, windowLayout(840f))
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        File(output).mkdirs()
        for (width in listOf(480, 720, 1100)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) {
                    AdaptiveAppShell(listOf(AppDestination("prompts", "Промпты", Icons.Default.Home), AppDestination("chat", "Чаты", Icons.Default.Forum), AppDestination("settings", "Настройки", Icons.Default.Settings)), "prompts", {}) {
                        PromptTextEditor("RU", "Черновик сохраняется при изменении размера окна.\n\nНапиши описание для {audience}.", {})
                    }
                } }
                scene.render(0).close()
                scene.render(1_000_000_000).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> File(output, "adaptive-shell-$width.png").writeBytes(data.bytes) } }
            } finally { scene.close() }
        }
    }
}
