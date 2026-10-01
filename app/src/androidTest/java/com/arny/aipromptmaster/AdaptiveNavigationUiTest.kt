package com.arny.aipromptmaster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.arny.aipromptmaster.ui.MainActivity
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editDraftSurvivesActivityRecreation() {
        compose.onAllNodesWithText("Промпты").onLast().performClick()
        compose.onNodeWithText("Создать", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Заголовок *").performTextInput("Fixture unsaved draft")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Fixture unsaved draft").assertExists()
    }

    @Test fun selectedSectionSurvivesRecreation() {
        compose.onAllNodesWithText("Настройки").onLast().performClick()
        compose.onNodeWithText("Провайдеры LLM").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Провайдеры LLM").assertExists()
    }
}
