package com.arny.aipromptmaster.domain.interactors

import app.cash.turbine.test
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Choice
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.Usage
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class LLMInteractorTest {

    @MockK
    private lateinit var modelsRepository: IOpenRouterRepository

    @MockK
    private lateinit var settingsRepository: ISettingsRepository

    @MockK
    private lateinit var historyRepository: IChatHistoryRepository

    @MockK
    private lateinit var fileRepository: IFileRepository

    private lateinit var interactor: LLMInteractor
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        interactor = LLMInteractor(
            modelsRepository = modelsRepository,
            settingsRepository = settingsRepository,
            historyRepository = historyRepository,
            fileRepository = fileRepository
        )
    }

    @Test
    fun `createNewConversation should return conversation id from repository`() = runTest {
        val expectedId = "test-conversation-id"
        val title = "Test Chat"
        coEvery { historyRepository.createNewConversation(title) } returns expectedId

        val result = interactor.createNewConversation(title)

        assertEquals(expectedId, result)
        coVerify { historyRepository.createNewConversation(title) }
    }

    @Test
    fun `setSystemPrompt should update system prompt in repository`() = runTest {
        val conversationId = "test-id"
        val prompt = "You are a helpful assistant"
        coEvery { historyRepository.updateSystemPrompt(conversationId, prompt) } returns Unit

        interactor.setSystemPrompt(conversationId, prompt)

        coVerify { historyRepository.updateSystemPrompt(conversationId, prompt) }
    }

    @Test
    fun `getSystemPrompt should return prompt from repository`() = runTest {
        val conversationId = "test-id"
        val expectedPrompt = "You are a helpful assistant"
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns expectedPrompt

        val result = interactor.getSystemPrompt(conversationId)

        assertEquals(expectedPrompt, result)
        coVerify { historyRepository.getSystemPrompt(conversationId) }
    }

    @Test
    fun `setSystemPromptWithChatCreation should create new conversation if id is null`() = runTest {
        val prompt = "System prompt"
        val chatTitle = "New Chat"
        val expectedId = "new-conversation-id"

        coEvery { historyRepository.createNewConversation(chatTitle) } returns expectedId
        coEvery { historyRepository.updateSystemPrompt(expectedId, prompt) } returns Unit

        val result = interactor.setSystemPromptWithChatCreation(null, prompt, chatTitle)

        assertEquals(expectedId, result)
        coVerify { historyRepository.createNewConversation(chatTitle) }
        coVerify { historyRepository.updateSystemPrompt(expectedId, prompt) }
    }

    @Test
    fun `setSystemPromptWithChatCreation should use existing conversation if id provided`() = runTest {
        val existingId = "existing-id"
        val prompt = "System prompt"
        val chatTitle = "Existing Chat"

        coEvery { historyRepository.updateSystemPrompt(existingId, prompt) } returns Unit

        val result = interactor.setSystemPromptWithChatCreation(existingId, prompt, chatTitle)

        assertEquals(existingId, result)
        coVerify(exactly = 0) { historyRepository.createNewConversation(any()) }
        coVerify { historyRepository.updateSystemPrompt(existingId, prompt) }
    }

    @Test
    fun `deleteConversation should delete conversation from repository`() = runTest {
        val conversationId = "test-id"
        coEvery { historyRepository.deleteConversation(conversationId) } returns Unit

        interactor.deleteConversation(conversationId)

        coVerify { historyRepository.deleteConversation(conversationId) }
    }

    @Test
    fun `toggleModelFavorite should add to favorites if not favorite`() = runTest {
        val modelId = "model-123"
        coEvery { settingsRepository.isFavorite(modelId) } returns false
        coEvery { settingsRepository.addToFavorites(modelId) } returns Unit

        interactor.toggleModelFavorite(modelId)

        coVerify { settingsRepository.isFavorite(modelId) }
        coVerify { settingsRepository.addToFavorites(modelId) }
    }

    @Test
    fun `toggleModelFavorite should remove from favorites if already favorite`() = runTest {
        val modelId = "model-123"
        coEvery { settingsRepository.isFavorite(modelId) } returns true
        coEvery { settingsRepository.removeFromFavorites(modelId) } returns Unit

        interactor.toggleModelFavorite(modelId)

        coVerify { settingsRepository.isFavorite(modelId) }
        coVerify { settingsRepository.removeFromFavorites(modelId) }
    }

    @Test
    fun `cancelCurrentRequest should cancel request in repository`() {
        coEvery { modelsRepository.cancelCurrentRequest() } returns Unit

        interactor.cancelCurrentRequest()

        coVerify { modelsRepository.cancelCurrentRequest() }
    }

    @Test
    fun `getFavoriteModels should filter favorite models from all models`() = runTest {
        val favoriteIds = setOf("model-2", "model-3")

        val models = listOf(
            createTestModel("model-1", isFavorite = false),
            createTestModel("model-2", isFavorite = false),
            createTestModel("model-3", isFavorite = false),
            createTestModel("model-4", isFavorite = false)
        )

        coEvery { modelsRepository.getModelsFlow() } returns flowOf(models)
        coEvery { settingsRepository.getSelectedModelId() } returns flowOf(null)
        coEvery { settingsRepository.getFavoriteModelIds() } returns flowOf(favoriteIds)

        interactor.getFavoriteModels().test {
            val firstItem = awaitItem()
            assertEquals(emptyList<LlmModel>(), firstItem)

            val favoriteModels = awaitItem()
            assertEquals(2, favoriteModels.size)

            val receivedIds = favoriteModels.map { it.id }.toSet()
            assertEquals(favoriteIds, receivedIds)

            assertTrue(favoriteModels.all { it.isFavorite })

            awaitComplete()
        }
    }

    @Test
    fun `getFullChatForExport should return formatted chat content`() = runTest {
        val conversationId = "test-id"
        val conversation = Conversation(
            id = conversationId,
            title = "Test Chat",
            systemPrompt = "You are a helpful assistant"
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "Hello"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "Hi there!")
        )

        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getFullHistory(conversationId) } returns messages

        val result = interactor.getFullChatForExport(conversationId)

        assertTrue(result.contains("# Диалог: Test Chat"))
        assertTrue(result.contains("## Системный промпт"))
        assertTrue(result.contains("You are a helpful assistant"))
        assertTrue(result.contains("## История диалога"))
        assertTrue(result.contains("👤 Пользователь"))
        assertTrue(result.contains("🤖 Ассистент"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("Hi there!"))
    }

    @Test
    fun `getFullChatForExport should throw error if conversation not found`() = runTest {
        val conversationId = "non-existent-id"
        coEvery { historyRepository.getConversation(conversationId) } returns null

        try {
            interactor.getFullChatForExport(conversationId)
            assertFalse("Should have thrown exception", true)
        } catch (e: Exception) {
            assertTrue(e is DomainError.Local)
        }
    }

    @Test
    fun `addUserMessageToHistory should create conversation if not exists`() = runTest {
        val conversationId = "test-id"
        val userMessage = "Hello, AI!"

        coEvery { historyRepository.getConversation(conversationId) } returns null
        coEvery { historyRepository.createNewConversation("Hello, AI!") } returns conversationId
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"

        interactor.addUserMessageToHistory(conversationId, userMessage)

        coVerify { historyRepository.createNewConversation("Hello, AI!") }
        coVerify {
            historyRepository.addMessage(conversationId, match { message ->
                message.role == ChatRole.USER && message.content == userMessage
            })
        }
    }

    @Test
    fun `addUserMessageToHistory should not create conversation if exists`() = runTest {
        val conversationId = "test-id"
        val userMessage = "Hello, AI!"
        val existingConversation = Conversation(conversationId, "Existing Chat", null)

        coEvery { historyRepository.getConversation(conversationId) } returns existingConversation
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"

        interactor.addUserMessageToHistory(conversationId, userMessage)

        coVerify(exactly = 0) { historyRepository.createNewConversation(any()) }
        coVerify {
            historyRepository.addMessage(conversationId, match { message ->
                message.role == ChatRole.USER && message.content == userMessage
            })
        }
    }

    @Test
    fun `addAssistantMessageToHistory should add assistant message`() = runTest {
        val conversationId = "test-id"
        val assistantMessage = "Hi there! How can I help you?"

        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"

        interactor.addAssistantMessageToHistory(conversationId, assistantMessage)

        coVerify {
            historyRepository.addMessage(conversationId, match { message ->
                message.role == ChatRole.ASSISTANT && message.content == assistantMessage
            })
        }
    }

    @Test
    fun `sendMessage should return error if API key not found`() = runTest {
        val model = "gpt-3.5-turbo"
        val conversationId = "test-id"

        coEvery { settingsRepository.getApiKey() } returns null
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(emptyList())

        interactor.sendMessage(model, conversationId).test {
            assertEquals(DataResult.Loading, awaitItem())
            val error = awaitItem() as DataResult.Error
            assertTrue(error.exception is DomainError.Local)
            awaitComplete()
        }
    }

    @Test
    fun `sendMessage should return successful response with usage info`() = runTest {
        val model = "gpt-3.5-turbo"
        val conversationId = "test-id"
        val apiKey = "test-api-key"
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        val response = ChatCompletionResponse(
            id = "chatcmpl-123",
            choices = listOf(Choice(ChatMessage(role = ChatRole.ASSISTANT, content = "Hi!"))),
            usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30)
        )

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery {
            modelsRepository.getChatCompletion(model, messages, apiKey)
        } returns Result.success(response)

        interactor.sendMessage(model, conversationId).test {
            assertEquals(DataResult.Loading, awaitItem())

            val success = awaitItem() as DataResult.Success
            val content = success.data
            assertTrue(content.contains("Hi!"))
            assertTrue(content.contains("📊 **Использование токенов:**"))
            assertTrue(content.contains("• Входящие: 10"))
            assertTrue(content.contains("• Исходящие: 20"))
            assertTrue(content.contains("• Всего: 30"))

            awaitComplete()
        }
    }

    @Test
    fun `sendMessage should return error on API failure`() = runTest {
        val model = "gpt-3.5-turbo"
        val conversationId = "test-id"
        val apiKey = "test-api-key"
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))
        val error = DomainError.Api(500, "Internal Server Error", "Server error")

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery {
            modelsRepository.getChatCompletion(model, messages, apiKey)
        } returns Result.failure(error)

        interactor.sendMessage(model, conversationId).test {
            assertEquals(DataResult.Loading, awaitItem())

            val errorResult = awaitItem() as DataResult.Error
            assertEquals(error, errorResult.exception)

            awaitComplete()
        }
    }

    @Test
    fun `sendMessageWithFallback should throw error if conversation ID is null`() = runTest {
        val model = "gpt-3.5-turbo"

        try {
            interactor.sendMessageWithFallback(model, null)
            assertFalse("Should have thrown exception", true)
        } catch (e: Exception) {
            assertTrue(e is DomainError.Local)
        }
    }

    @Test
    fun `sendMessageWithFallback should use file strategy when files attached`() = runTest {
        val model = "gpt-3.5-turbo"
        val conversationId = "test-id"
        val apiKey = "test-api-key"
        val systemPrompt = "You are a helpful assistant"

        val fileAttachment = FileAttachment(
            id = "file-1",
            fileName = "test.txt",
            fileExtension = "txt",
            fileSize = 100L,
            mimeType = "text/plain",
            originalContent = "File content"
        )

        val conversation = Conversation(conversationId, "Test Chat", systemPrompt)
        val messages = listOf(
            ChatMessage(
                role = ChatRole.USER,
                content = "Analyze this file",
                fileAttachment = FileAttachmentMetadata(
                    fileId = "file-1",
                    fileName = "test.txt",
                    fileExtension = "txt",
                    fileSize = 100L,
                    mimeType = "text/plain",
                    preview = "File content"
                )
            )
        )

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns systemPrompt
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery { fileRepository.getTemporaryFile("file-1") } returns fileAttachment
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"
        coEvery { historyRepository.appendContentToMessage(any(), any()) } returns Unit
        coEvery { modelsRepository.getChatCompletionStreamWithFiles(any(), apiKey) } returns flowOf(
            DataResult.Success("Response chunk")
        )

        interactor.sendMessageWithFallback(model, conversationId)

        coVerify { modelsRepository.getChatCompletionStreamWithFiles(any(), apiKey) }
    }

    @Test
    fun `sendMessageWithFallback should use regular strategy when no files attached`() = runTest {
        val model = "gpt-3.5-turbo"
        val conversationId = "test-id"
        val apiKey = "test-api-key"
        val systemPrompt = "You are a helpful assistant"

        val conversation = Conversation(conversationId, "Test Chat", systemPrompt)
        val messages = listOf(ChatMessage(role = ChatRole.USER, content = "Hello"))

        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getConversation(conversationId) } returns conversation
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns systemPrompt
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(messages)
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"
        coEvery { historyRepository.appendContentToMessage(any(), any()) } returns Unit
        coEvery { modelsRepository.getChatCompletionStream(any(), any(), apiKey) } returns flowOf(
            DataResult.Success("Response chunk")
        )

        interactor.sendMessageWithFallback(model, conversationId)

        coVerify { modelsRepository.getChatCompletionStream(any(), any(), apiKey) }
    }

    @Test
    fun `getChatList should return chat list from repository`() = runTest {
        val expectedChats = listOf(
            Chat(id = "chat-1", name = "Chat 1", timestamp = 123L, lastMessage = "Hello"),
            Chat(id = "chat-2", name = "Chat 2", timestamp = 456L, lastMessage = "Hi")
        )

        coEvery { historyRepository.getChatList() } returns flowOf(expectedChats)

        interactor.getChatList().test {
            val result = awaitItem()
            assertEquals(expectedChats, result)
            awaitComplete()
        }
    }

    @Test
    fun `getChatHistoryFlow should return history flow from repository`() = runTest {
        val conversationId = "test-id"
        val expectedMessages = listOf(
            ChatMessage(role = ChatRole.USER, content = "Hello"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "Hi there!")
        )

        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(expectedMessages)

        interactor.getChatHistoryFlow(conversationId).test {
            val result = awaitItem()
            assertEquals(expectedMessages, result)
            awaitComplete()
        }
    }

    @Test
    fun `clearChat should clear history in repository`() = runTest {
        val conversationId = "test-id"
        coEvery { historyRepository.clearHistory(conversationId) } returns Unit

        interactor.clearChat(conversationId)

        coVerify { historyRepository.clearHistory(conversationId) }
    }

    @Test
    fun `getModels should combine models with selection and favorites state`() = runTest {
        val models = listOf(
            createTestModel("model-1", isSelected = false, isFavorite = false),
            createTestModel("model-2", isSelected = true, isFavorite = true)
        )

        coEvery { modelsRepository.getModelsFlow() } returns flowOf(models)
        coEvery { settingsRepository.getSelectedModelId() } returns flowOf("model-2")
        coEvery { settingsRepository.getFavoriteModelIds() } returns flowOf(setOf("model-2"))

        interactor.getModels().test {
            val loading = awaitItem()
            assertEquals(DataResult.Loading, loading)

            val success = awaitItem() as DataResult.Success
            assertEquals(2, success.data.size)

            val selectedModel = success.data.find { it.isSelected }
            assertNotNull(selectedModel)
            assertEquals("model-2", selectedModel?.id)

            val favoriteModel = success.data.find { it.isFavorite }
            assertNotNull(favoriteModel)
            assertEquals("model-2", favoriteModel?.id)

            awaitComplete()
        }
    }

    @Test
    fun `getSelectedModel should return selected model from models list`() = runTest {
        val models = listOf(
            createTestModel("model-1", isSelected = false),
            createTestModel("model-2", isSelected = true)
        )

        coEvery { modelsRepository.getModelsFlow() } returns flowOf(models)
        coEvery { settingsRepository.getSelectedModelId() } returns flowOf("model-2")
        coEvery { settingsRepository.getFavoriteModelIds() } returns flowOf(emptySet())

        interactor.getSelectedModel().test {
            val loading = awaitItem()
            assertEquals(DataResult.Loading, loading)

            val success = awaitItem() as DataResult.Success
            assertEquals("model-2", success.data.id)
            assertTrue(success.data.isSelected)

            awaitComplete()
        }
    }

    @Test
    fun `getSelectedModel should return error if no model selected`() = runTest {
        val models = listOf(
            createTestModel("model-1", isSelected = false),
            createTestModel("model-2", isSelected = false)
        )

        coEvery { modelsRepository.getModelsFlow() } returns flowOf(models)
        coEvery { settingsRepository.getSelectedModelId() } returns flowOf(null)
        coEvery { settingsRepository.getFavoriteModelIds() } returns flowOf(emptySet())

        interactor.getSelectedModel().test {
            val loading = awaitItem()
            assertEquals(DataResult.Loading, loading)

            val result = awaitItem()

            when (result) {
                is DataResult.Success -> {
                    fail("Expected Error but got Success with data: ${result.data}")
                }
                is DataResult.Error -> {
                    assertNotNull("Error exception should not be null", result.exception)
                    assertTrue(
                        "Expected DomainError but got ${result.exception?.javaClass?.simpleName}",
                        result.exception is DomainError
                    )
                }
                else -> fail("Unexpected result type: ${result::class.simpleName}")
            }

            awaitComplete()
        }
    }

    @Test
    fun `selectModel should save selected model id to settings`() = runTest {
        val modelId = "model-123"
        coEvery { settingsRepository.setSelectedModelId(modelId) } returns Unit

        interactor.selectModel(modelId)

        coVerify { settingsRepository.setSelectedModelId(modelId) }
    }

    @Test
    fun `refreshModels should refresh models in repository`() = runTest {
        coEvery { modelsRepository.refreshModels() } returns Result.success(Unit)

        val result = interactor.refreshModels()

        assertTrue(result.isSuccess)
        coVerify { modelsRepository.refreshModels() }
    }

    @Test
    fun `toggleModelSelection should select model if not selected`() = runTest {
        val modelId = "model-123"
        val currentlySelectedId = "other-model"

        coEvery { settingsRepository.getSelectedModelId() } returns flowOf(currentlySelectedId)
        coEvery { settingsRepository.setSelectedModelId(modelId) } returns Unit

        interactor.toggleModelSelection(modelId)

        coVerify { settingsRepository.setSelectedModelId(modelId) }
    }

    @Test
    fun `toggleModelSelection should deselect model if already selected`() = runTest {
        val modelId = "model-123"

        coEvery { settingsRepository.getSelectedModelId() } returns flowOf(modelId)
        coEvery { settingsRepository.setSelectedModelId(null) } returns Unit

        interactor.toggleModelSelection(modelId)

        coVerify { settingsRepository.setSelectedModelId(null) }
    }

    @Test
    fun `addUserMessageWithFile should create conversation if not exists`() = runTest {
        val conversationId = "test-id"
        val userMessage = "Analyze this file"
        val fileAttachment = FileAttachment(
            id = "file-1",
            fileName = "test.txt",
            fileExtension = "txt",
            fileSize = 100L,
            mimeType = "text/plain",
            originalContent = "File content"
        )

        coEvery { historyRepository.getConversation(conversationId) } returns null
        coEvery { historyRepository.createNewConversation("Анализ файла: test.txt") } returns conversationId
        coEvery { fileRepository.saveTemporaryFile(fileAttachment) } returns "message-id"
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"

        interactor.addUserMessageWithFile(conversationId, userMessage, fileAttachment)

        coVerify { historyRepository.createNewConversation("Анализ файла: test.txt") }
        coVerify { fileRepository.saveTemporaryFile(fileAttachment) }
        coVerify {
            historyRepository.addMessage(conversationId, match { message ->
                message.role == ChatRole.USER && message.fileAttachment != null
            })
        }
    }

    @Test
    fun `addUserMessageWithFile should not create conversation if exists`() = runTest {
        val conversationId = "test-id"
        val userMessage = "Analyze this file"
        val fileAttachment = FileAttachment(
            id = "file-1",
            fileName = "test.txt",
            fileExtension = "txt",
            fileSize = 100L,
            mimeType = "text/plain",
            originalContent = "File content"
        )
        val existingConversation = Conversation(conversationId, "Existing Chat", null)

        coEvery { historyRepository.getConversation(conversationId) } returns existingConversation
        coEvery { fileRepository.saveTemporaryFile(fileAttachment) } returns "message-id"
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "message-id"

        interactor.addUserMessageWithFile(conversationId, userMessage, fileAttachment)

        coVerify(exactly = 0) { historyRepository.createNewConversation(any()) }
        coVerify { fileRepository.saveTemporaryFile(fileAttachment) }
        coVerify {
            historyRepository.addMessage(conversationId, match { message ->
                message.role == ChatRole.USER && message.fileAttachment != null
            })
        }
    }

    private fun createTestModel(
        id: String,
        isSelected: Boolean = false,
        isFavorite: Boolean = false
    ): LlmModel {
        return LlmModel(
            id = id,
            name = "Test Model $id",
            description = "Test model description",
            created = System.currentTimeMillis(),
            contextLength = BigDecimal(4096),
            pricingPrompt = BigDecimal("0.001"),
            pricingCompletion = BigDecimal("0.002"),
            pricingImage = null,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            isSelected = isSelected,
            isFavorite = isFavorite
        )
    }
}
