import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.results.DataResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatModelLabelTest {
    @Test fun selectedModelNameIsVisibleAndFallsBackToIdWhenNameIsMissing() {
        val model = LlmModel("provider/model", "Моя модель", "", 0, 32000, null, null, null, listOf("text"), listOf("text"), true)
        assertEquals("Моя модель", LlmUiState(modelsResult = DataResult.Success(listOf(model))).selectedModelLabel)
        assertEquals("provider/model", LlmUiState(modelsResult = DataResult.Success(listOf(model.copy(name = "")))).selectedModelLabel)
    }

    @Test fun sessionModelHasTheSamePriorityAsTheGenerationRequest() {
        val session = ChatSession("chat", "Test", null, ChatSettings(), 0, 0, modelId = "session/model")
        val selected = LlmModel("global/model", "Global", "", 0, 32000, null, null, null, listOf("text"), listOf("text"), true)
        val actual = selected.copy(id = "session/model", name = "Session", isSelected = false)
        assertEquals("Session", LlmUiState(selectedChatId = "chat", chatSessions = listOf(session), modelsResult = DataResult.Success(listOf(selected, actual))).selectedModelLabel)
    }

    @Test fun savedConversationModelRemainsVisibleWhileCatalogIsLoading() {
        val session = ChatSession("chat", "Чат", null, ChatSettings(), 0, 0, modelId = "saved/model")
        val state = LlmUiState(selectedChatId = session.id, chatSessions = listOf(session), modelsResult = DataResult.Loading)
        assertEquals("saved/model", state.selectedModelLabel)
    }
}
