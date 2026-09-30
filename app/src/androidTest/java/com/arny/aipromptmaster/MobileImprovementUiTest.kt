package com.arny.aipromptmaster

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.aipromptmaster.ui.providers.ProviderManagementCard
import com.arny.aipromptmaster.ui.screens.edit.*
import com.arny.promptcontract.*
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class MobileImprovementUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewAndExplicitApply() {
        var applied = false
        compose.setContent { MaterialTheme {
            PromptImprovementScreen(PromptImprovementState(visible = true, model = "fixture-model", source = "Напиши описание для {audience}", result = "Создай ясное описание для {audience}. Укажи преимущества и призыв к действию.", complete = true)) { if (it == PromptImprovementAction.Apply) applied = true }
        } }
        compose.onNodeWithText("Применить к черновику").assertIsEnabled()
        saveScreenshot("mobile-improvement.png", dialog = true)
        compose.onNodeWithText("Применить к черновику").performClick()
        compose.runOnIdle { assertEquals(true, applied) }
    }
    @Test fun incompletePreviewCannotApply() {
        compose.setContent { MaterialTheme { PromptImprovementScreen(PromptImprovementState(visible = true, model = "fixture", result = "Partial", running = true)) {} } }
        compose.onNodeWithText("Применить к черновику").assertIsNotEnabled()
        compose.onNodeWithText("Остановить").assertIsDisplayed()
        saveScreenshot("mobile-improvement-running.png", dialog = true)
    }
    @Test fun providerManagement() {
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ProviderManagementCard(ProviderManagerState(draft = ProviderProfile("local", "LM Studio", "http://10.0.2.2:1234/v1", modelId = "local-model", requiresKey = false)), onAction = {})
            }
        } }
        compose.onNodeWithText("Провайдеры LLM").assertIsDisplayed()
        saveScreenshot("mobile-providers.png", dialog = false)
    }
    @Test fun applyRemainsVisibleWithKeyboard() {
        compose.setContent { MaterialTheme {
            PromptImprovementScreen(PromptImprovementState(visible = true, model = "fixture-model", source = "Original", result = "Improved", complete = true)) {}
        } }
        compose.onNodeWithText("ID модели").performClick()
        compose.onNodeWithText("Применить к черновику").assertIsDisplayed()
        saveScreenshot("mobile-improvement-keyboard.png", dialog = true)
    }
    private fun saveScreenshot(name: String, dialog: Boolean) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val root = if (dialog) compose.onAllNodes(isRoot()).onLast() else compose.onRoot()
        val bitmap = if (dialog) InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() else root.captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
