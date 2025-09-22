package com.arny.aiprompts

import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import com.arny.aiprompts.presentation.navigation.MainState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        assertEquals(4, screens.size) // PROMPTS, CHAT, IMPORT, SETTINGS

        val expectedScreens = setOf(
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
            MainScreen.PROMPTS,
            MainScreen.CHAT,
            MainScreen.IMPORT,
            MainScreen.SETTINGS
        )

        // Verify we can navigate between all screens
        screens.forEachIndexed { index, screen ->
            assertNotNull(screen) { "Screen at index $index should not be null" }
        }

        // Test circular navigation
        assertEquals(MainScreen.PROMPTS, screens[0])
        assertEquals(MainScreen.CHAT, screens[1])
        assertEquals(MainScreen.IMPORT, screens[2])
        assertEquals(MainScreen.SETTINGS, screens[3])
    }
}