package com.arny.aipromptmaster.data.repositories


import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PromptsRepositoryImplTest {

    @MockK
    private lateinit var promptDao: PromptDao

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: PromptsRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = PromptsRepositoryImpl(promptDao, testDispatcher)
    }

    @Test
    fun `getPromptById SHOULD return mapped prompt WHEN dao finds entity`() = runTest(testDispatcher) {
        // Arrange
        val promptId = "123"
        // Создаем PromptEntity с отдельными полями для контента, как вы и указали.
        val fakeEntity = PromptEntity(
            id = promptId,
            title = "Test Title",
            description = "Test Description",
            contentRu = "Тестовый контент", // Используем отдельные поля
            contentEn = "Test Content",      //
            category = "Testing",
            status = "ACTIVE",
            compatibleModels = "model-1,model-2",
            tags = "tag1,tag2"
        )
        coEvery { promptDao.getById(promptId) } returns fakeEntity

        // Act
        val result = repository.getPromptById(promptId)

        // Assert
        // Проверяем, что объект PromptContent был смаплен корректно.
        val expectedContent = PromptContent(ru = "Тестовый контент", en = "Test Content")
        assertEquals(promptId, result?.id)
        assertEquals("Test Title", result?.title)
        assertEquals(expectedContent, result?.content) // Важная проверка!
    }

    @Test
    fun `updatePrompt SHOULD call dao's update method`() = runTest(testDispatcher) {
        // Arrange
        // Создаем валидный Prompt с корректным объектом PromptContent.
        val promptToUpdate = Prompt(
            id = "1",
            title = "Updated",
            description = "Updated text",
            content = PromptContent(ru = "Какой-то контент", en = "Some content"),
            compatibleModels = listOf("model-1"),
            category = "General",
            status = "DRAFT"
        )
        coEvery { promptDao.updatePrompt(any()) } returns Unit

        // Act
        repository.updatePrompt(promptToUpdate)

        // Assert
        coVerify(exactly = 1) { promptDao.updatePrompt(any()) }
    }
}
