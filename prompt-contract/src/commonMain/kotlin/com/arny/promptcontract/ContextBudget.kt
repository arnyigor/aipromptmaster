package com.arny.promptcontract

/** Conservative estimate, not a tokenizer. Reserve output and never silently truncate the newest input. */
fun <T> fitContextHistory(history: List<T>, systemText: String, contextTokens: Int, outputTokens: Int,
    text: (T) -> String, images: (T) -> Int = { 0 }): List<T> {
    fun cost(value: String) = (value.length.toLong() + 1) / 2 + 8
    val available = contextTokens.toLong() - outputTokens - cost(systemText) - 32
    require(available > 0) { "Системный промпт и лимит ответа превышают размер контекста модели" }
    if (history.isEmpty()) return emptyList()
    var used = 0L
    var start = history.size
    for (index in history.indices.reversed()) {
        val next = cost(text(history[index])) + images(history[index]).toLong() * 4096
        if (used + next > available) {
            require(index != history.lastIndex) { "Сообщение или вложения слишком велики для контекста модели" }
            break
        }
        used += next; start = index
    }
    return history.drop(start)
}
