package com.arny.promptcontract
import kotlin.test.*
import kotlinx.serialization.json.*
class CompletionPolicyTest {
 @Test fun modernAndLegacyEndpointsUseMatchingLimits() {
  val messages = buildJsonArray { addJsonObject { put("role", "user"); put("content", "Question") } }
  val modern = completionRequestBody(" o3 ", "https://api.openai.com/v1", messages, true, 128, 0.7, 0.9)
  assertEquals("o3", modern["model"]!!.jsonPrimitive.content)
  assertTrue(modern.containsKey("max_completion_tokens")); assertFalse(modern.containsKey("max_tokens"))
  assertFalse(modern.containsKey("temperature")); assertFalse(modern.containsKey("top_p"))
  val local = completionRequestBody("local", "http://localhost:1234/v1", messages, false, 128, 0.7)
  assertEquals(128, local["max_tokens"]!!.jsonPrimitive.int)
  assertEquals(0.7, local["temperature"]!!.jsonPrimitive.double)
 }
 @Test fun contextReservesOutputPreservesNewestAndRejectsOversizedInput() {
  assertEquals(listOf("new"), fitContextHistory(listOf("a".repeat(500), "new"), "system", 200, 50, { it }))
  assertFailsWith<IllegalArgumentException> { fitContextHistory(listOf("x".repeat(1000)), "", 200, 50, { it }) }
  assertFailsWith<IllegalArgumentException> { fitContextHistory(listOf("new"), "system", 200, 300, { it }) }
 }
 @Test fun sseAcceptsCommentsNoSpaceMultilineAndFinalFrame() {
  val parser = SseFrameDecoder()
  assertNull(parser.accept(": keepalive")); assertNull(parser.accept("data:first"))
  assertNull(parser.accept("data: second")); assertEquals("first\nsecond", parser.accept(""))
  parser.accept("data:[DONE]"); assertEquals("[DONE]", parser.finish()); assertNull(parser.finish())
 }
}
