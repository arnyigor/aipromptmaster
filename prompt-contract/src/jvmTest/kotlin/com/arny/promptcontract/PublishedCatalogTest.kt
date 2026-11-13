package com.arny.promptcontract

import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedCatalogTest {
    @Test fun verifiesAndDecodesEveryPackagedPrompt() {
        val path = System.getenv("PROMPT_CATALOG_ARCHIVE")
        assumeTrue("External catalog package supplied for local acceptance check", path != null)
        ZipFile(File(path!!)).use { archive ->
            val files = archive.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".json") }
                .associate { it.name to archive.getInputStream(it).use { stream -> stream.readBytes() } }
            val source = archive.getInputStream(archive.getEntry(CatalogManifest.FILE_NAME)).use { it.readBytes().decodeToString() }
            val manifest = CatalogManifest.verify(source, files) {
                MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { byte -> "%02x".format(byte) }
            }
            val json = Json { ignoreUnknownKeys = true }
            files.values.forEach { json.decodeFromString<PromptJson>(it.decodeToString()) }
            assertEquals(manifest.count, files.size)
        }
    }
}
