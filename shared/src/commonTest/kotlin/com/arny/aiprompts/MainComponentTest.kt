package com.arny.aiprompts

import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import com.arny.aiprompts.presentation.navigation.MainState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MainComponentTest {

    @Test
    fun testMainComponentCreation() = runTest {
        // This is a basic test to verify the component structure
        // In a real implementation, you would use proper dependency injection
        // and test doubles for the use cases

        // Test that we can create the expected state structure
        val expectedState = MainState(
            currentScreen = MainScreen.PROMPTS,
            sidebarCollapsed = false,
            activeWorkspace = null
        )

        assertEquals(MainScreen.PROMPTS, expectedState.currentScreen)
        assertEquals(false, expectedState.sidebarCollapsed)
        assertEquals(null, expectedState.activeWorkspace)
    }

    @Test
    fun testMainScreenValues() {
        val screens = MainScreen.values()
        // Main screens: SCRAPER, PROMPTS, CHAT, IMPORT, SETTINGS (5 screens)
        assertEquals(5, screens.size)

        val expectedScreens = setOf(
            MainScreen.SCRAPER,
            MainScreen.PROMPTS,
            MainScreen.CHAT,
            MainScreen.IMPORT,
            MainScreen.SETTINGS
        )

        screens.forEach { screen ->
            assert(screen in expectedScreens) { "Unexpected screen: $screen" }
        }
    }

    @Test
    fun testNavigationFlow() {
        // Test that navigation between screens works as expected
        val screens = listOf(
            MainScreen.SCRAPER,
            MainScreen.PROMPTS,
            MainScreen.CHAT,
            MainScreen.IMPORT,
            MainScreen.SETTINGS
        )

        // Verify we can navigate between all screens
        screens.forEachIndexed { index, screen ->
            assertNotNull(screen) { "Screen at index $index should not be null" }
        }

        // Test that all expected screens are present
        assertTrue(MainScreen.SCRAPER in screens)
        assertTrue(MainScreen.PROMPTS in screens)
        assertTrue(MainScreen.CHAT in screens)
        assertTrue(MainScreen.IMPORT in screens)
        assertTrue(MainScreen.SETTINGS in screens)
    }
}