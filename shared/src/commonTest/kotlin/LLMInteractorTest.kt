import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.Choice
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.domain.interactors.LLMInteractor
import com.arny.aiprompts.results.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class LLMInteractorTest {

    private val mockModelsRepository = mockk<IOpenRouterRepository>()
    private val mockSettingsRepository = mockk<ISettingsRepository>()
    private val mockHistoryRepository = mockk<IChatHistoryRepository>()
    private val interactor = LLMInteractor(mockModelsRepository, mockSettingsRepository, mockHistoryRepository)

    @Test
    fun `sendMessage emits loading then success when API call succeeds`() = runTest {
        // Given
        val model = "gpt-4"
        val userMessage = "Hello"
        val apiKey = "test-api-key"
        val history = listOf<ChatMessage>()
        val response = ChatCompletionResponse(
            choices = listOf(
                Choice(
                    message = ChatMessage(
                        role = ChatMessageRole.MODEL,
                        content = "Hi there",
                        timestamp = 0
                    )
                )
            )
        )

        every { mockHistoryRepository.getHistoryFlow() } returns flowOf(history)
        coEvery { mockHistoryRepository.addMessages(any()) } returns Unit
        coEvery { mockSettingsRepository.getApiKey() } returns apiKey
        coEvery { mockModelsRepository.getChatCompletion(model, any(), apiKey) } returns Result.success(response)

        // When
        val results = interactor.sendMessage(model, userMessage).toList()

        // Then
        assertEquals(2, results.size) // Loading, Success
        assertTrue(results[0] is DataResult.Loading)
        assertTrue(results[1] is DataResult.Success)
        val successResult = results[1] as DataResult.Success
        assertEquals("Hi there", successResult.data)
    }

    @Test
    fun `sendMessage emits error when API key is missing`() = runTest {
        // Given
        val model = "gpt-4"
        val userMessage = "Hello"
        val history = listOf<ChatMessage>()

        every { mockHistoryRepository.getHistoryFlow() } returns flowOf(history)
        coEvery { mockSettingsRepository.getApiKey() } returns null

        // When
        val results = interactor.sendMessage(model, userMessage).toList()

        // Then
        assertEquals(2, results.size) // Loading, Error
        assertTrue(results[0] is DataResult.Loading)
        assertTrue(results[1] is DataResult.Error)
    }

    @Test
    fun `sendMessage emits error when API call fails`() = runTest {
        // Given
        val model = "gpt-4"
        val userMessage = "Hello"
        val apiKey = "test-api-key"
        val history = listOf<ChatMessage>()
        val exception = RuntimeException("API error")

        every { mockHistoryRepository.getHistoryFlow() } returns flowOf(history)
        coEvery { mockHistoryRepository.addMessages(any()) } returns Unit
        coEvery { mockSettingsRepository.getApiKey() } returns apiKey
        coEvery { mockModelsRepository.getChatCompletion(model, any(), apiKey) } returns Result.failure(exception)

        // When
        val results = interactor.sendMessage(model, userMessage).toList()

        // Then
        assertEquals(2, results.size) // Loading, Error
        assertTrue(results[0] is DataResult.Loading)
        assertTrue(results[1] is DataResult.Error)
    }

    @Test
    fun `getChatHistoryFlow returns history from repository`() = runTest {
        // Given
        val history = listOf(ChatMessage(role = ChatMessageRole.USER, content = "Test", timestamp = 0))
        every { mockHistoryRepository.getHistoryFlow() } returns flowOf(history)

        // When
        val result = interactor.getChatHistoryFlow()

        // Then
        val collected = result.toList()
        assertEquals(1, collected.size)
        assertEquals(history, collected.first())
    }

    @Test
    fun `clearChat calls repository clearHistory`() = runTest {
        // Given
        coEvery { mockHistoryRepository.clearHistory() } returns Unit

        // When
        interactor.clearChat()

        // Then
        coVerify { mockHistoryRepository.clearHistory() }
    }

    @Test
    fun `selectModel calls repository setSelectedModelId`() = runTest {
        // Given
        val modelId = "gpt-4"
        coEvery { mockSettingsRepository.setSelectedModelId(modelId) } returns Unit

        // When
        interactor.selectModel(modelId)

        // Then
        coVerify { mockSettingsRepository.setSelectedModelId(modelId) }
    }

    @Test
    fun `refreshModels calls repository refreshModels and handles success`() = runTest {
        // Given
        coEvery { mockModelsRepository.refreshModels() } returns Result.success(Unit)

        // When
        val result = interactor.refreshModels()

        // Then
        assertTrue(result.isSuccess)
        coVerify { mockModelsRepository.refreshModels() }
    }

    @Test
    fun `refreshModels calls repository refreshModels and handles failure`() = runTest {
        // Given
        val exception = RuntimeException("Refresh failed")
        coEvery { mockModelsRepository.refreshModels() } returns Result.failure(exception)

        // When
        val result = interactor.refreshModels()

        // Then
        assertTrue(result.isFailure)
        coVerify { mockModelsRepository.refreshModels() }
    }

    @Test
    fun `toggleModelSelection selects model when none selected`() = runTest {
        // Given
        val modelId = "gpt-4"
        every { mockSettingsRepository.getSelectedModelId() } returns flowOf(null)
        coEvery { mockSettingsRepository.setSelectedModelId(modelId) } returns Unit

        // When
        interactor.toggleModelSelection(modelId)

        // Then
        coVerify { mockSettingsRepository.setSelectedModelId(modelId) }
    }

    @Test
    fun `toggleModelSelection deselects model when same selected`() = runTest {
        // Given
        val modelId = "gpt-4"
        every { mockSettingsRepository.getSelectedModelId() } returns flowOf(modelId)
        coEvery { mockSettingsRepository.setSelectedModelId(null) } returns Unit

        // When
        interactor.toggleModelSelection(modelId)

        // Then
        coVerify { mockSettingsRepository.setSelectedModelId(null) }
    }
}