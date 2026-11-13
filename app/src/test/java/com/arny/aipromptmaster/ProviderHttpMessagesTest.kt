package com.arny.aipromptmaster

import com.arny.aipromptmaster.domain.models.errors.providerHttpMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHttpMessagesTest {
    @Test
    fun cloudflareBlockIsNotReportedAsBadKey() {
        val text = providerHttpMessage(403, """{"success":false,"error":"Access denied by security policy."}""")
        assertTrue(text, text.contains("заблокировал"))
        assertTrue(text, !text.contains("Ключ"))
    }

    @Test
    fun unauthorizedMentionsKey() {
        assertTrue(providerHttpMessage(401).contains("Ключ"))
    }

    @Test
    fun rateLimitAndServerErrors() {
        assertTrue(providerHttpMessage(429).contains("Слишком много"))
        assertTrue(providerHttpMessage(503).contains("503"))
    }

    @Test
    fun plainForbiddenMentionsAccess() {
        assertTrue(providerHttpMessage(403).contains("403"))
    }
}
