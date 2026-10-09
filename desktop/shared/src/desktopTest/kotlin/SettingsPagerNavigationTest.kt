import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.arny.sharedui.SyncTabPager
import com.arny.sharedui.TabPager
import kotlin.test.Test
import kotlin.test.assertEquals
import com.arny.aiprompts.presentation.screens.SettingsComponent
import com.arny.aiprompts.presentation.screens.SettingsState
import com.arny.aiprompts.presentation.ui.settings.SettingsScreen
import com.arny.promptcontract.ProviderManagerState
import com.arny.sharedui.PromptTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class SettingsPagerNavigationTest {
    @Test fun restoredSelectionOverridesDefaultPagerWithoutNavigationFeedback() {
        val callbacks = mutableListOf<Int>()
        lateinit var pager: PagerState
        val scene = ImageComposeScene(360, 900)
        try {
            scene.setContent {
                pager = rememberPagerState(initialPage = 0, pageCount = { 5 })
                SyncTabPager(pager, 4) { callbacks.add(it) }
                TabPager(pager, swipeEnabled = true) { }
            }
            repeat(5) { scene.render(it * 100_000_000L).close() }
            assertEquals(4, pager.currentPage)
            assertEquals(emptyList(), callbacks)
        } finally { scene.close() }
    }

    @Test fun renderCompactSettings() {
        val output = System.getenv("PROMPT_SCREENSHOT_DIR") ?: return
        val settings = mockk<SettingsComponent>(relaxed = true)
        every { settings.state } returns MutableStateFlow(SettingsState())
        every { settings.personalVault } returns MutableStateFlow(com.arny.promptcontract.PersonalVaultUi())
        every { settings.providers } returns MutableStateFlow(ProviderManagerState())
        for (width in listOf(360, 480)) {
            val scene = ImageComposeScene(width, 900)
            try {
                scene.setContent { PromptTheme(dark = false) { SettingsScreen(settings) } }
                scene.render(0).close()
                scene.render(1_000_000_000L).use { rendered ->
                    rendered.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(output, "settings-$width.png").writeBytes(it.bytes)
                    }
                }
            } finally { scene.close() }
        }
    }

    @Test fun jumpingToSettingsDoesNotSelectIntermediateTabs() {
        val selected = mutableStateOf(0)
        val swipeSelections = mutableListOf<Int>()
        lateinit var pager: PagerState
        val scene = ImageComposeScene(480, 900)
        try {
            scene.setContent {
                pager = rememberPagerState(pageCount = { 5 })
                SyncTabPager(pager, selected.value) { swipeSelections.add(it); selected.value = it }
                TabPager(pager, swipeEnabled = true) { }
            }
            var frame = 0L
            for (destination in listOf(0, 4, 1, 4, 0)) {
                selected.value = destination
                repeat(5) { scene.render(frame++ * 100_000_000L).close() }
                assertEquals(destination, selected.value)
                assertEquals(destination, pager.currentPage)
            }
            assertEquals(emptyList(), swipeSelections)
        } finally { scene.close() }
    }
}
