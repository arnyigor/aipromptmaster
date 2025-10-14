package com.arny.aipromptmaster.domain.interactors

import app.cash.turbine.test
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Choice
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
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

    @MockK
    private lateinit var fileRepository: IFileRepository

    private lateinit var interactor: ILLMInteractor

    @BeforeEach
    fun setUp() {
        interactor =
            LLMInteractor(modelsRepository, settingsRepository, historyRepository, fileRepository)
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
    fun `addUserMessageWithFile SHOULD save file and message with metadata`() = runTest {
        // Arrange
        val conversationId = "conv_123"
        val userMessage = "Analyze this file"
        val fileContent = "public class Test { }"
        val fileAttachment = FileAttachment(
            id = "file_456",
            fileName = "Test.java",
            fileExtension = "java",
            fileSize = fileContent.length.toLong(),
            mimeType = "text/x-java",
            originalContent = fileContent
        )

        coEvery { historyRepository.getConversation(conversationId) } returns null
        coEvery { historyRepository.createNewConversation(any()) } returns conversationId
        coEvery { fileRepository.saveTemporaryFile(fileAttachment) } returns fileAttachment.id
        coEvery { historyRepository.addMessages(any(), any()) } just runs

        // Act
        interactor.addUserMessageWithFile(conversationId, userMessage, fileAttachment)

        // Assert
        coVerify { fileRepository.saveTemporaryFile(fileAttachment) }
        coVerify {
            historyRepository.addMessages(
                eq(conversationId),
                match { messages ->
                    messages.size == 1 &&
                            messages[0].role == ChatRole.USER &&
                            messages[0].content.contains("Analyze this file") &&
                            messages[0].content.contains("📎 **Файл**: Test.java") &&
                            messages[0].fileAttachment?.fileId == "file_456" &&
                            messages[0].fileAttachment?.preview == fileContent // <= 500 chars
                }
            )
        }
    }

    @Test
    fun `getFullChatForExport SHOULD include attached files in markdown`() = runTest {
        // Arrange
        val conversationId = "conv_789"
        val systemPrompt = "You are a code reviewer."
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = "Review this:\n📎 **Файл**: Test.java\n**Размер**: 22 B\n**Превью**:\n```...",
            fileAttachment = FileAttachmentMetadata(
                fileId = "file_101",
                fileName = "Test.java",
                fileExtension = "java",
                fileSize = 22L,
                mimeType = "text/x-java",
                preview = "public class Test { }"
            )
        )
        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = "Looks good!")

        val fullFile = FileAttachment(
            id = "file_101",
            fileName = "Test.java",
            fileExtension = "java",
            fileSize = 22L,
            mimeType = "text/x-java",
            originalContent = "public class Test { }"
        )

        coEvery { historyRepository.getConversation(conversationId) } returns Conversation(
            conversationId,
            "Code Review",
            systemPrompt
        )
        coEvery { historyRepository.getFullHistory(conversationId) } returns listOf(
            userMessage,
            assistantMessage
        )
        coEvery { fileRepository.getTemporaryFile("file_101") } returns fullFile

        // Act
        val exported = interactor.getFullChatForExport(conversationId)

        // Assert
        assertTrue(exported.contains("# Диалог: Code Review"))
        assertTrue(exported.contains("## Системный промпт"))
        assertTrue(exported.contains(systemPrompt))
        assertTrue(exported.contains("## Прикрепленные файлы"))
        assertTrue(exported.contains("### Файл 1: Test.java"))
        assertTrue(exported.contains("```java\npublic class Test { }\n```"))
        assertTrue(exported.contains("👤 Пользователь"))
        assertTrue(exported.contains("Review this:"))
        assertTrue(exported.contains("🤖 Ассистент"))
        assertTrue(exported.contains("Looks good!"))
    }

    @Test
    fun `sendMessageWithFallback SHOULD stream response and append to message WHEN file is attached`() = runTest {
        // Arrange
        val conversationId = "conv_202"
        val model = "gpt-4o"
        val apiKey = "sk-123"
        val fileContent = """{"key": "value"}"""
        val fileAttachment = FileAttachment(
            id = "file_999",
            fileName = "data.json",
            fileExtension = "json",
            fileSize = fileContent.length.toLong(),
            mimeType = "application/json",
            originalContent = fileContent
        )

        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = "Analyze this\n📎 **Файл**: data.json...",
            fileAttachment = FileAttachmentMetadata(
                fileId = "file_999",
                fileName = "data.json",
                fileExtension = "json",
                fileSize = fileContent.length.toLong(),
                mimeType = "application/json",
                preview = fileContent.take(500)
            )
        )

        // Моки
        coEvery { settingsRepository.getApiKey() } returns apiKey
        coEvery { historyRepository.getSystemPrompt(conversationId) } returns null
        coEvery { historyRepository.getHistoryFlow(conversationId) } returns flowOf(listOf(userMessage))
        coEvery { fileRepository.getTemporaryFile("file_999") } returns fileAttachment
        coEvery { historyRepository.addMessage(conversationId, any()) } returns "msg_111"
        coEvery { historyRepository.appendContentToMessage(any(), any()) } just runs
        coEvery { historyRepository.deleteMessage(any()) } just runs

        val fakeResponseFlow = flowOf(
            DataResult.Success("First"),
            DataResult.Success(" second.")
        )

        coEvery {
            modelsRepository.getChatCompletionStreamWithFiles(
                match { req ->
                    req.model == model &&
                            req.files.size == 1 &&
                            req.files[0].id == "file_999" &&
                            req.files[0].content == fileContent
                },
                eq(apiKey)
            )
        } returns fakeResponseFlow

        // Act
        interactor.sendMessageWithFallback(model, conversationId, estimatedTokensBeforeRequest)

        // Assert
        coVerify(exactly = 1) {
            historyRepository.addMessage(eq(conversationId), any())
        }

        coVerify(exactly = 2) {
            historyRepository.appendContentToMessage(eq("msg_111"), any())
        }

        coVerify {
            historyRepository.appendContentToMessage("msg_111", "First")
            historyRepository.appendContentToMessage("msg_111", " second.")
        }

        // Убеждаемся, что сообщение НЕ удалялось (успешный сценарий)
        coVerify(exactly = 0) {
            historyRepository.deleteMessage(any())
        }
    }
}
