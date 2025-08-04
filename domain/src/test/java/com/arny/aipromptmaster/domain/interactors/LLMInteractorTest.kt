package com.arny.aipromptmaster.domain.interactors

import app.cash.turbine.test
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Choice
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class LLMInteractorTest {

    @MockK
    private lateinit var modelsRepository: IOpenRouterRepository
    @MockK
    private lateinit var settingsRepository: ISettingsRepository
    @MockK
    private lateinit var historyRepository: IChatHistoryRepository

    private lateinit var interactor: LLMInteractor

    @BeforeEach
    fun setUp() {
        interactor = LLMInteractor(modelsRepository, settingsRepository, historyRepository)
    }

    @Test
    fun `sendMessage SHOULD return error WHEN api key is missing`() = runTest {
        // Arrange
        coEvery { settingsRepository.getApiKey() } returns null

        // Act & Assert
        interactor.sendMessage("test_model", "test_conversation_id").test {
            assertEquals(DataResult.Loading, awaitItem())

            val errorResult = awaitItem() as DataResult.Error
            val exception = errorResult.exception
            assertTrue(exception is DomainError.Local)

            val expectedError = DomainError.local(R.string.api_key_not_found)
            assertEquals(expectedError, exception)

            awaitComplete()
        }
    }

    @Test
    fun `sendMessage SHOULD return success WHEN api call is successful`() = runTest {
        // Arrange
        val fakeApiKey = "valid_api_key"
        val fakeResponseContent = "Hello from AI!"
        val fakeHistory = listOf(ChatMessage(role = ChatRole.USER, content = "Hi"))

        // Создаем фальшивый ответ, используя НОВЫЕ доменные модели:
        // ChatCompletionResponse и Choice.
        val fakeApiResponse = ChatCompletionResponse(
            id = "fake_response_id_123",
            choices = listOf(
                Choice(
                    message = ChatMessage(role = ChatRole.ASSISTANT, content = fakeResponseContent)
                )
            ),
            usage = null // usage может быть null, так что это валидный сценарий
        )

        coEvery { settingsRepository.getApiKey() } returns fakeApiKey
        coEvery { historyRepository.getHistoryFlow(any()) } returns flowOf(fakeHistory)
        // Метод getChatCompletion теперь должен возвращать Result<ChatCompletionResponse>
        coEvery { modelsRepository.getChatCompletion(any(), any(), fakeApiKey) } returns Result.success(fakeApiResponse)

        // Act & Assert
        interactor.sendMessage("test_model", "test_conversation_id").test {
            assertEquals(DataResult.Loading, awaitItem())

            val successResult = awaitItem() as DataResult.Success
            assertEquals(fakeResponseContent, successResult.data)

            awaitComplete()
        }
    }
}
