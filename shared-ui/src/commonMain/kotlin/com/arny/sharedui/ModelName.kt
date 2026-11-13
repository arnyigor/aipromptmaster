package com.arny.sharedui

/**
 * Model labels come from providers: API ids (`openrouter/free`) and local file paths
 * (`G:\...\Qwen3-1.7B-Q4_K_M.gguf`). Paths are shortened to the file name without extension,
 * API ids are kept as-is.
 */
fun shortModelName(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return ""
    val looksLikePath = value.contains('\\') || value.startsWith("/") ||
        PATH_PREFIX.containsMatchIn(value) || value.endsWith(".gguf", ignoreCase = true)
    if (!looksLikePath) return value
    val file = value.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".gguf")
    return file.ifBlank { value }
}

private val PATH_PREFIX = Regex("^[A-Za-z]:")
