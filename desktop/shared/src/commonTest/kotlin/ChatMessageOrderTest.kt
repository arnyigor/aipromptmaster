@file:OptIn(kotlin.time.ExperimentalTime::class)
import com.arny.aiprompts.data.db.daos.*
import com.arny.aiprompts.data.db.entities.ChatMessageEntity
import com.arny.aiprompts.data.model.*
import com.arny.aiprompts.data.repositories.ChatSessionRepositoryImpl
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ChatMessageOrderTest {
    @Test fun `stream update preserves database order and session`() = runTest {
        val dao = mockk<ChatMessageDao>(relaxed = true)
        val existing = ChatMessageEntity("response", "chat", "assistant", "Partial", 1, "{}", orderIndex = 7)
        coEvery { dao.getMessageById("response") } returns existing
        val repository = ChatSessionRepositoryImpl(mockk<ChatSessionDao>(), dao)
        repository.updateMessage(ChatMessage("response", ChatMessageRole.MODEL, "Completed", timestamp = 1))
        coVerify { dao.updateMessage(match { it.orderIndex == 7 && it.sessionId == "chat" && it.content == "Completed" }) }
    }
}
