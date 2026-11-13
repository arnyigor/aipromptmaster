import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.db.entities.ChatSessionEntity
import com.arny.aiprompts.data.repositories.ChatSessionRepositoryImpl
import com.arny.aiprompts.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*
class ChatSessionSafetyTest {
 @Test fun concurrentFieldUpdatesSummariesAndArchiveWorkInRealRoom() = runTest {
  val directory = Files.createTempDirectory("chat-safety").toFile()
  val db = Room.databaseBuilder<AppDatabase>(name=java.io.File(directory,"test.db").absolutePath)
   .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
  try {
   val repository = ChatSessionRepositoryImpl(db.chatSessionDao(), db.chatMessageDao())
   db.chatSessionDao().insertSession(ChatSessionEntity("chat","Old",null,createdAt=0,updatedAt=0))
   coroutineScope {
    launch { repository.renameSession("chat","New") }
    launch { repository.updateSystemPrompt("chat","System") }
    launch { repository.updateSettings("chat",ChatSettings(maxTokens=777)) }
    launch { repository.updateModel("chat","chosen/model","provider") }
   }
   val stored = db.chatSessionDao().getSessionById("chat")!!
   assertEquals("New",stored.name); assertEquals("System",stored.systemPrompt)
   assertEquals(777,stored.maxTokens); assertEquals("provider",stored.providerId)
   repository.addMessage("chat",ChatMessage(role=ChatMessageRole.MODEL,content="Last answer",tokenCount=17))
   val summary=repository.getAllSessions().first().single()
   assertEquals("Last answer",summary.getLastMessagePreview()); assertEquals(17,summary.totalTokenCount)
   assertTrue(summary.messages.isEmpty())
   repository.archiveSession("chat"); assertTrue(repository.getAllSessions().first().single().isArchived)
   repository.unarchiveSession("chat"); assertFalse(repository.getAllSessions().first().single().isArchived)
  } finally { db.close(); directory.deleteRecursively() }
 }
}
