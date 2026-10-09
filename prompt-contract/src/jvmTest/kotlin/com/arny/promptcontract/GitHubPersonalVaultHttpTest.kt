package com.arny.promptcontract

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class GitHubPersonalVaultHttpTest {
    private val config = PersonalVaultConfig("owner/private", token = "fake-test-token")
    private val snapshot = PersonalVaultSnapshot(prompts = listOf(PromptJson(id = "a", title = "Личный", isLocal = true, content = mapOf("ru" to "Текст")))).checked()
    @Test fun contentsTransportReadsPrivateRepoAndWritesBase64WithExpectedSha() = runTest {
        val requests = mutableListOf<String>(); var uploaded: JsonObject? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/owner/private") { exchange ->
            try {
                requests.add(exchange.requestMethod + " " + exchange.requestURI.toString())
                assertEquals("Bearer fake-test-token", exchange.requestHeaders.getFirst("Authorization"))
                assertFalse(exchange.requestURI.toString().contains(config.token))
                val body = when {
                    exchange.requestURI.path.endsWith("/private") -> """{"private":true,"default_branch":"main"}"""
                    exchange.requestMethod == "GET" -> buildJsonObject {
                        put("sha", "old-sha"); put("encoding", "base64")
                        put("content", Base64.getMimeEncoder().encodeToString(Json.encodeToString(PersonalVaultSnapshot.serializer(), snapshot).encodeToByteArray()))
                    }.toString()
                    else -> { uploaded = Json.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject; "{}" }
                }
                val bytes = body.encodeToByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } finally { exchange.close() }
        }
        server.start()
        try {
            val transport = JvmGitHubPersonalVault("http://127.0.0.1:${server.address.port}")
            val downloaded = transport.read(config)
            assertEquals(snapshot, downloaded.snapshot); assertEquals("main", downloaded.branch)
            transport.write(config.copy(branch = downloaded.branch), snapshot, downloaded.sha)
            val body = assertNotNull(uploaded)
            assertEquals("old-sha", body.getValue("sha").jsonPrimitive.content)
            assertEquals("main", body.getValue("branch").jsonPrimitive.content)
            assertEquals(snapshot, Json.decodeFromString<PersonalVaultSnapshot>(Base64.getDecoder().decode(body.getValue("content").jsonPrimitive.content).decodeToString()))
            assertTrue(requests[1].endsWith("?ref=main")); assertFalse(requests.last().contains("?"))
        } finally { server.stop(0) }
    }
    @Test fun staleShaAndPublicRepositoryAreRejected() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = if (exchange.requestMethod == "GET") """{"private":false,"default_branch":"main"}""" else "{}"
            val bytes = body.encodeToByteArray(); exchange.sendResponseHeaders(if (exchange.requestMethod == "PUT") 409 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.start()
        try {
            val transport = JvmGitHubPersonalVault("http://127.0.0.1:${server.address.port}")
            assertFailsWith<IllegalArgumentException> { transport.read(config) }
            val conflict = assertFailsWith<IllegalStateException> { transport.write(config, snapshot, "stale") }
            assertTrue(conflict.message!!.contains("изменился")); assertFalse(conflict.message!!.contains(config.token))
        } finally { server.stop(0) }
    }
}
