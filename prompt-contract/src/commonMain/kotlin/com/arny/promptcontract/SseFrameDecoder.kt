package com.arny.promptcontract

/** SSE data fields may omit the space and span several lines. Comments are ignored. */
class SseFrameDecoder {
    private val data = mutableListOf<String>()
    fun accept(line: String): String? {
        if (line.isEmpty()) return finish()
        if (line.startsWith("data:")) data += line.substring(5).removePrefix(" ")
        return null
    }
    fun finish(): String? {
        if (data.isEmpty()) return null
        val value = data.joinToString("\n")
        data.clear()
        return value
    }
}
