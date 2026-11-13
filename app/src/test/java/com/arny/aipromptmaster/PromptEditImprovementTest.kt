package com.arny.aipromptmaster

import com.arny.aipromptmaster.domain.ImprovePromptUseCase
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.*
import com.arny.aipromptmaster.ui.screens.edit.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PromptEditImprovementTest {
    @Test fun `applying EN changes only draft and saves personal metadata explicitly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val original = Prompt(id = "public-id", title = "Title", description = null, category = "general", content = PromptContent("Исходный", "Original"), isLocal = false, isFavorite = true, variables = mapOf("name" to "value"))
            val interactor = mockk<IPromptsInteractor>(relaxed = true)
            coEvery { interactor.getPrompt("public-id") } returns original
            coEvery { interactor.getUniqueCategories() } returns listOf("general")
            coEvery { interactor.updatePrompt(any()) } returns true
            val improvement = mockk<ImprovePromptUseCase>()
            coEvery { improvement.selectedModel() } returns "fixture-model"
            every { improvement(any()) } returns flowOf("Improved EN")
            val vm = PromptEditViewModel("public-id", interactor, improvement)
            advanceUntilIdle()
            vm.openImprovement(); advanceUntilIdle()
            vm.onImprovementAction(PromptImprovementAction.Language(PromptLanguage.EN))
            vm.onImprovementAction(PromptImprovementAction.Generate); advanceUntilIdle()
            assertEquals("Original", vm.uiState.value.contentEn)
            vm.onImprovementAction(PromptImprovementAction.Apply)
            assertEquals("Improved EN", vm.uiState.value.contentEn)
            assertEquals("Исходный", vm.uiState.value.contentRu)
            coVerify(exactly = 0) { interactor.updatePrompt(any()) }
            vm.onSaveClicked(); advanceUntilIdle()
            coVerify { interactor.updatePrompt(match { it.isLocal && it.isFavorite && it.variables == original.variables && it.content.en == "Improved EN" && it.content.ru == "Исходный" }) }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `cancelled partial result cannot modify mobile editor`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val improvement = mockk<ImprovePromptUseCase>()
            coEvery { improvement.selectedModel() } returns "model"
            every { improvement(any()) } returns flow { emit("Partial"); awaitCancellation() }
            val vm = PromptEditViewModel(null, mockk(relaxed = true), improvement)
            vm.updateContentRu("Исходный"); vm.openImprovement(); runCurrent()
            vm.onImprovementAction(PromptImprovementAction.Generate); runCurrent()
            vm.onImprovementAction(PromptImprovementAction.Cancel)
            vm.onImprovementAction(PromptImprovementAction.Apply); runCurrent()
            assertEquals("Исходный", vm.uiState.value.contentRu)
            assertFalse(vm.improvement.value.canApply)
        } finally { Dispatchers.resetMain() }
    }
}
