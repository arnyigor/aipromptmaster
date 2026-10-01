@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arny.aiprompts.domain.files.FileMetadataReader
import com.arny.aiprompts.domain.interfaces.*
import com.arny.aiprompts.domain.model.*
import com.arny.aiprompts.domain.usecase.*
import com.arny.aiprompts.presentation.ui.importer.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Instant

class ImporterWorkflowTest {
    @Test fun `checkbox preloads unvisited posts errors recover and repeated import reuses id`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val lifecycle = LifecycleRegistry()
        val directory = Files.createTempDirectory("import-workflow").toFile()
        try {
            val input = File(directory, "input.html").apply { writeText("test input") }
            val posts = listOf("one", "two").map {
                RawPostData(it, Author("author", "Author"), Instant.fromEpochMilliseconds(1), fullHtmlContent = "Content $it", isLikelyPrompt = true)
            }
            val parser = mockk<IFileParser> { every { parse(any()) } returns posts }
            val hybrid = mockk<IHybridParser> { every { analyzeAndExtract(any()) } answers {
                EditedPostData(title = firstArg(), content = firstArg(), category = "general")
            } }
            val source = mockk<FileDataSource>()
            coEvery { source.savePromptJson(any()) } coAnswers {
                File(directory, "${firstArg<com.arny.aiprompts.data.model.PromptJson>().id}.json").apply { writeText("saved") }
            }
            val library = mockk<IPromptsRepository>(relaxed = true)
            val metadata = mockk<FileMetadataReader> { coEvery { readAllSourceIds(any()) } returns emptyMap() }
            val component = DefaultImporterComponent(DefaultComponentContext(lifecycle), listOf(input),
                ParseRawPostsUseCase(parser), SavePromptsAsFilesUseCase(source, library), hybrid,
                mockk(), mockk(relaxed = true), metadata, {})
            withTimeout(5000) { while (component.state.value.rawPosts.size != 2 || component.state.value.isLoading) delay(10) }
            component.onTogglePostForImport("two", true)
            assertNotNull(component.state.value.editedData["two"])
            component.onPostClicked("two")
            val valid = component.state.value.currentEditedData!!
            component.onEditDataChanged(valid.copy(content = ""))
            assertFalse(component.validateEditedData("two"))
            assertFalse(component.state.value.canGenerateJson)
            component.onEditDataChanged(valid)
            assertTrue(component.state.value.canGenerateJson)
            component.onImportClicked()
            withTimeout(5000) { while (component.state.value.isLoading) delay(10) }
            assertNull(component.state.value.error)
            assertEquals(1, component.state.value.savedFiles.size)
            assertTrue(component.state.value.postsToImport.isEmpty())
            val savedPath = component.state.value.savedFiles["two"]
            component.onTogglePostForImport("two", true)
            component.onImportClicked()
            withTimeout(5000) { while (component.state.value.isLoading) delay(10) }
            assertEquals(savedPath, component.state.value.savedFiles["two"])
            coVerify(exactly = 2) { library.savePrompts(match { it.single().isLocal }) }
            assertEquals(1, directory.listFiles()!!.count { it.extension == "json" })
            component.onPostClicked("one")
            component.onSkipPostClicked()
            assertTrue("one" in component.state.value.skippedPostIds)
            assertNotEquals("one", component.state.value.selectedPostId)
        } finally {
            lifecycle.destroy()
            Dispatchers.resetMain()
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
