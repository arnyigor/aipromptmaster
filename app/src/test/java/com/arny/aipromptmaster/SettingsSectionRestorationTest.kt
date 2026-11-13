package com.arny.aipromptmaster

import androidx.lifecycle.SavedStateHandle
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.ui.providers.ProvidersViewModel
import com.arny.promptcontract.ProviderConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsSectionRestorationTest {
    @Test fun selectedSettingsSectionSurvivesOwnerRecreationAndIgnoresUnknownRoutes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = mockk<ISettingsRepository>(relaxed = true)
            every { settings.loadProviders() } returns ProviderConfig()
            val savedState = SavedStateHandle()
            val first = ProvidersViewModel(settings, mockk(relaxed = true), mockk(relaxed = true), savedState)
            first.onSection("GITHUB")
            first.onSection("invalid")
            val restored = ProvidersViewModel(settings, mockk(relaxed = true), mockk(relaxed = true),
                SavedStateHandle(mapOf("settings-section" to savedState.get<String>("settings-section"))))
            assertEquals("GITHUB", restored.section.value)
            restored.onSection("FEEDBACK")
            assertEquals("FEEDBACK", restored.section.value)
        } finally { Dispatchers.resetMain() }
    }
}
