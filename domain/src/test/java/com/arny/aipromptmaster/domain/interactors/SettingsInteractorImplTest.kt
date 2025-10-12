package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.repositories.IFeedbackRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SettingsInteractorImplTest {

    private lateinit var feedbackRepository: IFeedbackRepository
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var interactor: SettingsInteractorImpl

    @Before
    fun setup() {
        feedbackRepository = mockk()
        settingsRepository = mockk()
        interactor = SettingsInteractorImpl(feedbackRepository, settingsRepository)
    }

    @Test
    fun `saveApiKey should delegate to settings repository`() {
        // Arrange
        val apiKey = "test-api-key"
        every { settingsRepository.saveApiKey(apiKey) } returns Unit

        // Act
        interactor.saveApiKey(apiKey)

        // Assert
        verify { settingsRepository.saveApiKey(apiKey) }
    }

    @Test
    fun `getApiKey should delegate to settings repository and return key`() {
        // Arrange
        val expectedApiKey = "test-api-key"
        every { settingsRepository.getApiKey() } returns expectedApiKey

        // Act
        val result = interactor.getApiKey()

        // Assert
        assertEquals(expectedApiKey, result)
        verify { settingsRepository.getApiKey() }
    }

    @Test
    fun `getApiKey should return null when no key is stored`() {
        // Arrange
        every { settingsRepository.getApiKey() } returns null

        // Act
        val result = interactor.getApiKey()

        // Assert
        assertNull(result)
        verify { settingsRepository.getApiKey() }
    }

    @Test
    fun `sendFeedback should delegate to feedback repository and return success result`() = runTest {
        // Arrange
        val feedbackContent = "This is test feedback"
        val expectedResult = Result.success(Unit)
        coEvery { feedbackRepository.sendFeedback(feedbackContent) } returns expectedResult

        // Act
        val result = interactor.sendFeedback(feedbackContent)

        // Assert
        assertEquals(expectedResult, result)
        coVerify { feedbackRepository.sendFeedback(feedbackContent) }
    }

    @Test
    fun `sendFeedback should handle error result from repository`() = runTest {
        // Arrange
        val feedbackContent = "This is test feedback"
        val exception = RuntimeException("Network error")
        val expectedResult = Result.failure<Unit>(exception)
        coEvery { feedbackRepository.sendFeedback(feedbackContent) } returns expectedResult

        // Act
        val result = interactor.sendFeedback(feedbackContent)

        // Assert
        assertEquals(expectedResult, result)
        coVerify { feedbackRepository.sendFeedback(feedbackContent) }
    }

    @Test
    fun `sendFeedback should handle empty feedback content`() = runTest {
        // Arrange
        val feedbackContent = ""
        val expectedResult = Result.success(Unit)
        coEvery { feedbackRepository.sendFeedback(feedbackContent) } returns expectedResult

        // Act
        val result = interactor.sendFeedback(feedbackContent)

        // Assert
        assertEquals(expectedResult, result)
        coVerify { feedbackRepository.sendFeedback(feedbackContent) }
    }

    @Test
    fun `sendFeedback should handle long feedback content`() = runTest {
        // Arrange
        val feedbackContent = "A".repeat(1000) // Long feedback content
        val expectedResult = Result.success(Unit)
        coEvery { feedbackRepository.sendFeedback(feedbackContent) } returns expectedResult

        // Act
        val result = interactor.sendFeedback(feedbackContent)

        // Assert
        assertEquals(expectedResult, result)
        coVerify { feedbackRepository.sendFeedback(feedbackContent) }
    }
}