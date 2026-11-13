import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.ProviderManagerState
import com.arny.sharedui.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class UiPolishTest {
    @Test fun navigationAnimationKeepsOneOwnerAndDoesNotRecomposeContentEveryFrame() {
        val selected = mutableStateOf("prompts")
        val scene = ImageComposeScene(360, 650)
        var owners = 0
        var compositions = 0
        try {
            scene.setContent { PromptTheme(dark = false) {
                AdaptiveAppShell(listOf(AppDestination("prompts", "Промпты", Icons.Default.Home)), selected.value, {}) {
                    remember { owners++ }
                    SideEffect { compositions++ }
                    Text("Stable screen")
                }
            } }
            repeat(15) { scene.render(it * 16_000_000L).close() }
            selected.value = "chat"
            repeat(30) { scene.render((it + 15) * 16_000_000L).close() }
            assertEquals(1, owners)
            assertTrue(compositions <= 4, "Draw animation recomposed content $compositions times")
        } finally { scene.close() }
    }

    @Test fun settingsSectionsKeepSeparateSavedStateDuringRapidSwitchAndResize() {
        val selected = mutableStateOf("ONE")
        lateinit var field: MutableState<String>
        val scene = ImageComposeScene(360, 600)
        var frame = 0L
        fun render() { repeat(4) { scene.render(frame++ * 100_000_000L).close() } }
        try {
            scene.setContent { PromptTheme(dark = false) {
                SettingsPane(ProviderManagerState(), {}, tabs = listOf(SettingsTabUi("ONE", "Первый"), SettingsTabUi("TWO", "Второй")),
                    selectedId = selected.value, extraContent = { section ->
                        field = rememberSaveable { mutableStateOf(section) }
                        TextField(field.value, { field.value = it })
                    })
            } }
            render()
            field.value = "Saved first section"
            selected.value = "TWO"; render()
            assertEquals("TWO", field.value, "New section inherited another section's state")
            field.value = "Saved second section"
            selected.value = "ONE"; render()
            assertEquals("Saved first section", field.value)
            scene.constraints = Constraints.fixed(1440, 900); render()
            assertEquals("Saved first section", field.value)
            selected.value = "TWO"; render()
            assertEquals("Saved second section", field.value)
        } finally { scene.close() }
    }

    @Test fun renderLightDarkCompactShortAndWideStates() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        File(output).mkdirs()
        for (dark in listOf(false, true)) for (width in listOf(320, 360, 760, 1440)) {
            for (screen in listOf("prompts", "composer", "providers")) {
                val scene = ImageComposeScene(width, 650, density = Density(1f, if (width == 320) 1.3f else 1f))
                try {
                    scene.setContent { PromptTheme(dark = dark) { Surface(Modifier.fillMaxSize()) {
                        when (screen) {
                            "prompts" -> PromptBrowser(PromptBrowserUi(prompts = List(9) { index ->
                                PromptCardUi("$index", "План развития приложения", "Помоги составить понятный план и определить следующие шаги.",
                                    listOf("Разработка", "Личный"), favorite = index == 0)
                            }, categories = listOf("Разработка", "Работа"), sortOptions = listOf("По имени"), sort = "По имени"),
                                {}, {}, {}, {}, {}, onSort = {}, onFavoritesOnly = {})
                            "composer" -> Column(Modifier.fillMaxSize()) {
                                Text("AI Chat", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                ChatComposer("Подготовь пошаговый план улучшения моего приложения.\nУчти мобильную и desktop-версии.", {}, {}, {},
                                    generating = false, canSend = true, onClear = {}, onAttach = {})
                            }
                            else -> SettingsPane(ProviderManagerState(), {}, tabs = listOf(SettingsTabUi("API", "Модели"),
                                SettingsTabUi("GITHUB", "GitHub"), SettingsTabUi("PROFILE", "Профиль")))
                        }
                    } } }
                    scene.render(0).close()
                    scene.render(1_000_000_000L).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(output, "polish-$screen-${if (dark) "dark" else "light"}-$width.png").writeBytes(it.bytes)
                    } }
                } finally { scene.close() }
            }
        }
    }
}
