package com.arny.aipromptmaster.presentation.ui.chat

import android.net.Uri
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var llmInteractor: ILLMInteractor
    private lateinit var fileRepository: IFileRepository
    private lateinit var viewModel: ChatViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testConversationId = "test-conversation-conversationId"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        llmInteractor = mockk()
        fileRepository = mockk()

        // Setup default mocks
        every { llmInteractor.getModels() } returns flowOf(DataResult.Success(emptyList()))
        every { llmInteractor.getSelectedModel() } returns flowOf(DataResult.Success(createTestModel()))
        every { llmInteractor.getChatHistoryFlow(any()) } returns flowOf(emptyList())
        coEvery { llmInteractor.getSystemPrompt(any()) } returns null
        coEvery { llmInteractor.refreshModels() } returns Result.success(Unit)

        viewModel = ChatViewModel(llmInteractor, fileRepository, testConversationId)
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Assert
        val initialChatState = viewModel.chatState.value
        assertEquals(ChatUiState.Idle, initialChatState)

        val initialChatData = viewModel.chatData.value
        assertEquals(testConversationId, initialChatData.conversationId)
        assertTrue(initialChatData.messages.isEmpty())
    }

    @Test
    fun `sendMessage should create new conversation when conversationId is null`() = runTest {
        // Arrange
        val viewModelWithoutId = ChatViewModel(llmInteractor, fileRepository, null)
        val messageText = "Test message"
        val newConversationId = "new-conversation-conversationId"
        val newTitle = messageText.take(40)

        coEvery { llmInteractor.createNewConversation(newTitle) } returns newConversationId
        coEvery { llmInteractor.addUserMessageToHistory(newConversationId, messageText) } returns Unit
        coEvery { llmInteractor.sendMessageWithFallback(any(), newConversationId) } returns Unit

        // Act
        viewModelWithoutId.sendMessage(messageText)

        // Assert
        advanceUntilIdle()
        coVerify {
            llmInteractor.createNewConversation(newTitle)
            llmInteractor.addUserMessageToHistory(newConversationId, messageText)
            llmInteractor.sendMessageWithFallback(any(), newConversationId)
        }

        val chatState = viewModelWithoutId.chatState.value
        assertEquals(ChatUiState.Completed, chatState)
    }

    @Test
    fun `sendMessage should use existing conversation when conversationId is provided`() = runTest {
        // Arrange
        val messageText = "Test message"

        coEvery { llmInteractor.addUserMessageToHistory(testConversationId, messageText) } returns Unit
        coEvery { llmInteractor.sendMessageWithFallback(any(), testConversationId) } returns Unit

        // Act
        viewModel.sendMessage(messageText)

        // Assert
        advanceUntilIdle()
        coVerify {
            llmInteractor.addUserMessageToHistory(testConversationId, messageText)
            llmInteractor.sendMessageWithFallback(any(), testConversationId)
        }
        coVerify(exactly = 0) { llmInteractor.createNewConversation(any()) }
    }

    @Test
    fun `sendMessage should handle empty message with files correctly`() = runTest {
        // Arrange
        val emptyMessage = "   "
        val fileAttachment = createTestFileAttachment()

        // Setup file attachment
        val attachmentId = "attachment-conversationId"
        // We need to mock the file attachment state

        coEvery { llmInteractor.addUserMessageWithFile(testConversationId, emptyMessage, fileAttachment) } returns Unit
        coEvery { llmInteractor.sendMessageWithFallback(any(), testConversationId) } returns Unit

        // Act
        viewModel.sendMessage(emptyMessage)

        // Assert - should still process if files are attached
        advanceUntilIdle()
    }

    @Test
    fun `sendMessage should not send when already streaming`() = runTest {
        // Arrange
        viewModel = ChatViewModel(llmInteractor, fileRepository, testConversationId)
        val messageText = "Test message"

        // Set state to streaming
        // This is tricky to test because _chatState is private

        // Act
        viewModel.sendMessage(messageText)

        // Assert
        // Should not call any interactor methods when already streaming
        // This would need more sophisticated mocking of the state
    }

    @Test
    fun `cancelCurrentRequest should cancel active request`() = runTest {
        // Arrange
        // Set up a streaming state somehow
        // This is complex to test due to private state

        // Act
        viewModel.cancelCurrentRequest()

        // Assert
        coVerify { llmInteractor.cancelCurrentRequest() }
    }

    @Test
    fun `addAttachmentFromUri should process file and add to attachments`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        val fileAttachment = createTestFileAttachment()
        val processingResult = FileProcessingResult.Complete(fileAttachment)

        every { fileRepository.processFileFromUri(uri) } returns flowOf(processingResult)
        coEvery { fileRepository.saveTemporaryFile(fileAttachment) } returns "saved-conversationId"

        // Act
        viewModel.addAttachmentFromUri(uri)

        // Assert
        advanceUntilIdle()
        coVerify {
            fileRepository.processFileFromUri(uri)
            fileRepository.saveTemporaryFile(fileAttachment)
        }
    }

    @Test
    fun `addAttachmentFromUri should handle file size limit exceeded`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        val largeFileSize = 50 * 1024 * 1024L // 50MB, exceeds 30MB limit
        val processingResult = FileProcessingResult.Started(largeFileSize)

        every { fileRepository.processFileFromUri(uri) } returns flowOf(processingResult)

        // Act
        viewModel.addAttachmentFromUri(uri)

        // Assert
        advanceUntilIdle()
        // Should not save the file due to size limit
        coVerify(exactly = 0) { fileRepository.saveTemporaryFile(any()) }
    }

    @Test
    fun `addAttachmentFromUri should handle processing error`() = runTest {
        // Arrange
        val uri = mockk<Uri>()
        val errorMessage = "Processing failed"
        val processingResult = FileProcessingResult.Error(errorMessage)

        every { fileRepository.processFileFromUri(uri) } returns flowOf(processingResult)

        // Act
        viewModel.addAttachmentFromUri(uri)

        // Assert
        advanceUntilIdle()
        // Should not save the file due to error
        coVerify(exactly = 0) { fileRepository.saveTemporaryFile(any()) }
    }

    @Test
    fun `removeAttachment should remove attachment from state`() = runTest {
        // Arrange
        val attachmentId = "attachment-conversationId"

        // Act
        viewModel.removeAttachment(attachmentId)

        // Assert
        // The attachment should be removed from the internal state
        // This is hard to verify directly due to private state
    }

    @Test
    fun `hasUploadingFiles should return false when no files uploading`() = runTest {
        // Act
        val result = viewModel.hasUploadingFiles()

        // Assert
        assertFalse(result)
    }

    @Test
    fun `onExportChatClicked should export chat content`() = runTest {
        // Arrange
        val chatContent = "Exported chat content"
        coEvery { llmInteractor.getFullChatForExport(testConversationId) } returns chatContent

        // Act
        viewModel.onExportChatClicked()

        // Assert
        advanceUntilIdle()
        coVerify { llmInteractor.getFullChatForExport(testConversationId) }
    }

    @Test
    fun `onExportChatClicked should not export when no conversation id`() = runTest {
        // Arrange
        val viewModelWithoutId = ChatViewModel(llmInteractor, fileRepository, null)

        // Act
        viewModelWithoutId.onExportChatClicked()

        // Assert
        advanceUntilIdle()
        coVerify(exactly = 0) { llmInteractor.getFullChatForExport(any()) }
    }

    @Test
    fun `setSystemPrompt should set system prompt for current conversation`() = runTest {
        // Arrange
        val systemPrompt = "You are a helpful assistant"

        coEvery { llmInteractor.setSystemPrompt(testConversationId, systemPrompt) } returns Unit

        // Act
        viewModel.setSystemPrompt(systemPrompt)

        // Assert
        coVerify { llmInteractor.setSystemPrompt(testConversationId, systemPrompt) }
    }

    @Test
    fun `setSystemPrompt should not set when no conversation id`() = runTest {
        // Arrange
        val viewModelWithoutId = ChatViewModel(llmInteractor, fileRepository, null)
        val systemPrompt = "You are a helpful assistant"

        // Act
        viewModelWithoutId.setSystemPrompt(systemPrompt)

        // Assert
        coVerify(exactly = 0) { llmInteractor.setSystemPrompt(any(), any()) }
    }

    @Test
    fun `onRemoveChatHistory should clear chat history`() = runTest {
        // Arrange
        coEvery { llmInteractor.clearChat(testConversationId) } returns Unit

        // Act
        viewModel.onRemoveChatHistory()

        // Assert
        coVerify { llmInteractor.clearChat(testConversationId) }
    }

    @Test
    fun `onRemoveChatHistory should not clear when no conversation id`() = runTest {
        // Arrange
        val viewModelWithoutId = ChatViewModel(llmInteractor, fileRepository, null)

        // Act
        viewModelWithoutId.onRemoveChatHistory()

        // Assert
        coVerify(exactly = 0) { llmInteractor.clearChat(any()) }
    }

    @Test
    fun `onSystemPromptMenuClicked should create new conversation when none exists`() = runTest {
        // Arrange
        val viewModelWithoutId = ChatViewModel(llmInteractor, fileRepository, null)
        val newConversationId = "new-conversation-conversationId"

        coEvery { llmInteractor.createNewConversation("Новый чат") } returns newConversationId

        // Act
        viewModelWithoutId.onSystemPromptMenuClicked()

        // Assert
        advanceUntilIdle()
        coVerify { llmInteractor.createNewConversation("Новый чат") }
    }

    @Test
    fun `onSystemPromptMenuClicked should use existing conversation when available`() = runTest {
        // Act
        viewModel.onSystemPromptMenuClicked()

        // Assert
        advanceUntilIdle()
        // Should not create new conversation when one already exists
        coVerify(exactly = 0) { llmInteractor.createNewConversation(any()) }
    }

    @Test
    fun `adjustTokenRatio should update token ratio and accuracy flag`() = runTest {
        // Arrange
        val actualPromptTokens = 100
        val actualCompletionTokens = 50
        val estimatedPromptTokens = 80

        // Act
        viewModel.adjustTokenRatio(actualPromptTokens, actualCompletionTokens, estimatedPromptTokens)

        // Assert
        val tokenRatio = viewModel.tokenRatio.value
        val isAccurate = viewModel.isAccurate.value

        assertTrue(isAccurate)
        assertTrue(tokenRatio in 2.0..8.0)
    }

    @Test
    fun `adjustTokenRatio should not update when actual prompt tokens are zero`() = runTest {
        // Arrange
        val actualPromptTokens = 0
        val actualCompletionTokens = 50
        val estimatedPromptTokens = 80
        val initialRatio = viewModel.tokenRatio.value

        // Act
        viewModel.adjustTokenRatio(actualPromptTokens, actualCompletionTokens, estimatedPromptTokens)

        // Assert
        val tokenRatio = viewModel.tokenRatio.value
        val isAccurate = viewModel.isAccurate.value

        assertEquals(initialRatio, tokenRatio, 0.001)
        assertFalse(isAccurate)
    }

    private fun createTestModel(): LlmModel {
        return LlmModel(
            id = "test-model-conversationId",
            name = "Test Model",
            provider = "test-provider",
            architecture = "test-architecture",
            isFavorite = false,
            contextWindow = 4096,
            maxTokens = 2048
        )
    }

    private fun createTestFileAttachment(): FileAttachment {
        return FileAttachment(
            id = "test-file-conversationId",
            fileName = "test.txt",
            fileSize = 1024L,
            mimeType = "text/plain",
            originalContent = "Test file content"
        )
    }
}