package com.arny.aipromptmaster

import androidx.lifecycle.SavedStateHandle
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.*
import com.arny.aipromptmaster.ui.screens.edit.PromptEditViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EditorRestorationTest {
    @Test fun restoredDraftKeepsBothLanguagesAndVariantsWithoutWritingDatabase() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = mockk<IPromptsInteractor>(relaxed = true)
            coEvery { repository.getUniqueCategories() } returns listOf("general")
            val handle = SavedStateHandle()
            val first = PromptEditViewModel(null, repository, savedState = handle)
            first.updateTitle("Unsaved draft"); first.updateCategory("general")
            first.updateContentRu("Черновик {name}"); first.updateContentEn("Draft {name}")
            first.addVariant()
            first.updateVariant(0, first.uiState.value.variants.first().copy(content = PromptContent("Вариант", "Variant")))
            runCurrent()
            val recreated = PromptEditViewModel(null, repository, savedState = SavedStateHandle(mapOf("prompt-edit-draft" to handle.get<String>("prompt-edit-draft"))))
            runCurrent()
            assertEquals(first.uiState.value, recreated.uiState.value)
            coVerify(exactly = 0) { repository.updatePrompt(any()) }
        } finally { Dispatchers.resetMain() }
    }
}
