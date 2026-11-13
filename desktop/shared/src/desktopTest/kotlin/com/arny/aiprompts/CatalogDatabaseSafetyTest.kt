package com.arny.aiprompts

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.db.entities.PromptEntity
import com.arny.aiprompts.data.db.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CatalogDatabaseSafetyTest {
    @Test fun `transaction preserves private and favorite records when processing tombstones`() = runTest {
        val directory = Files.createTempDirectory("catalog-room").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = File(directory, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.promptDao()
            fun record(id: String, personal: Boolean = false, favorite: Boolean = false) =
                PromptEntity(id = id, title = id, description = null, status = "active", isLocal = personal, isFavorite = favorite)
            dao.insertPrompts(listOf(record("private", true), record("favorite", favorite = true), record("public")))
            dao.syncCatalog(listOf(record("private").copy(title = "remote"), record("new")), listOf("private", "favorite", "public"))
            assertEquals("private", dao.getById("private")?.title)
            assertNotNull(dao.getById("favorite"))
            assertEquals(null, dao.getById("public"))
            assertNotNull(dao.getById("new"))
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test fun `exported desktop schemas migrate without losing private data`() = runTest {
        for (version in 1..3) {
            val directory = Files.createTempDirectory("catalog-migration").toFile()
            val file = File(directory, "test.db")
            val schema = Json.parseToJsonElement(File("schemas/com.arny.aiprompts.data.db.AppDatabase/$version.json").readText()).jsonObject.getValue("database").jsonObject
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                fun sql(value: String) { connection.prepare(value).use { it.step() } }
                schema.getValue("entities").jsonArray.forEach { element ->
                    val entity = element.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    sql(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    entity.getValue("indices").jsonArray.forEach { sql(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { sql(it.jsonPrimitive.content) }
                sql("PRAGMA user_version = $version")
                sql("INSERT INTO prompts VALUES ('private','Private',NULL,'Text','','{}','','general','',1,1,0,0,'active','','','','','1','','')")
            }
            val db = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).addMigrations(*ALL_MIGRATIONS)
                .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
            try {
                assertEquals("Text", db.promptDao().getById("private")?.contentRu)
            } finally {
                db.close()
                directory.deleteRecursively()
            }
        }
    }
}
