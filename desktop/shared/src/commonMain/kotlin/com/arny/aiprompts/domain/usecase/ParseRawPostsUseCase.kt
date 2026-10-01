package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.model.RawPostData
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseRawPostsUseCase(private val parser: IFileParser) {
    suspend operator fun invoke(file: File): Result<List<RawPostData>> {
        return try {
            withContext(Dispatchers.IO) {
                val bytes = file.readBytes()
                val header = bytes.take(8192).toByteArray().toString(Charsets.ISO_8859_1)
                val declared = Regex("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                    .find(header)?.groupValues?.get(1)
                val charset = declared?.let(Charset::forName) ?: Charsets.UTF_8
                Result.success(parser.parse(bytes.toString(charset).removePrefix("\uFEFF")))
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
