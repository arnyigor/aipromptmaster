import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.arny.aiprompts.platform.DesktopSessionCheckpoint
import com.arny.aiprompts.platform.DesktopSessionStore
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import com.arny.aiprompts.presentation.screens.PromptDetailEvent
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*

object DesktopCrashFixture {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        System.setProperty("aiprompts.settings.namespace", args[0])
        val store = DesktopSessionStore()
        val keeper = StateKeeperDispatcher(store.load())
        val root = withContext(Dispatchers.Main) { MultiStackRestorationTest().createRoot(LifecycleRegistry(), keeper) }
        if (args[1] == "seed") {
            withContext(Dispatchers.Main) { root.navigateToPromptDetails("fixture") }
            delay(300)
            withContext(Dispatchers.Main) {
                val details = (root.childStack.value.active.instance as MainComponent.Child.PromptDetails).component
                details.onEvent(PromptDetailEvent.EditClicked)
                details.onEvent(PromptDetailEvent.TitleChanged("CRASH_DRAFT"))
                root.navigateToChat()
                (root.childStack.value.active.instance as MainComponent.Child.Chat).component.onPromptChanged("CRASH_CHAT_INPUT")
            }
            launch { DesktopSessionCheckpoint(keeper, store, intervalMillis = 100).run() }
            withTimeout(30_000) { while (store.load() == null) delay(100) }
            File(args[2]).writeText("READY")
            awaitCancellation() // The parent kills this process; no graceful close/save handler runs.
        } else {
            check(root.state.value.currentScreen == MainScreen.CHAT)
            check((root.childStack.value.active.instance as MainComponent.Child.Chat).component.uiState.value.prompt == "CRASH_CHAT_INPUT")
            withContext(Dispatchers.Main) { root.navigateToPrompts() }
            check((root.childStack.value.active.instance as MainComponent.Child.PromptDetails).component.state.value.draftPrompt?.title == "CRASH_DRAFT")
            File(args[2]).writeText("RESTORED")
        }
    }
}

class DesktopCrashRecoveryTest {
    @Test fun forcedProcessTerminationRestoresTabDraftAndChat() {
        val folder = Files.createTempDirectory("aiprompts-crash-test").toFile()
        val namespace = "crash-test-${UUID.randomUUID()}"
        val marker = File(folder, "checkpoint.txt")
        val java = File(System.getProperty("java.home"), "bin/java" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
        val classpath = File(System.getProperty("aiprompts.test.classpathFile") ?: error("Missing test classpath")).readText()
        val processes = mutableListOf<Process>()
        fun launch(phase: String): Process {
            val args = File(folder, "$phase.args")
            fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            args.writeText(listOf("-cp", quoted(classpath), "DesktopCrashFixture", namespace, phase, quoted(marker.absolutePath)).joinToString("\n"))
            return ProcessBuilder(java.absolutePath, "@${args.absolutePath}").redirectErrorStream(true)
                .redirectOutput(File(folder, "$phase.log")).start().also(processes::add)
        }
        try {
            val original = launch("seed")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while (!marker.exists() && original.isAlive && System.nanoTime() < deadline) Thread.sleep(100)
            assertTrue(marker.exists(), "Seed process did not checkpoint; diagnostics: ${folder.absolutePath}")
            original.destroyForcibly()
            assertTrue(original.waitFor(10, TimeUnit.SECONDS))
            val restored = launch("restore")
            assertTrue(restored.waitFor(45, TimeUnit.SECONDS), "Restore process timed out")
            assertEquals(0, restored.exitValue(), "Restore failed; diagnostics: ${folder.absolutePath}")
            assertEquals("RESTORED", marker.readText())
        } finally { processes.filter { it.isAlive }.forEach { it.destroyForcibly() } }
    }
}
