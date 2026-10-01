package com.arny.aipromptmaster

import androidx.room.testing.MigrationTestHelper
import androidx.room.Room
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import kotlinx.coroutines.runBlocking
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.arny.aipromptmaster.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class DatabaseMigrationTest {
    @Test fun historical1MigratesTo8() = migrateHistorical(1)
    @Test fun historical2MigratesTo8() = migrateHistorical(2)
    @Test fun historical3MigratesTo8() = migrateHistorical(3)

    private fun migrateHistorical(version: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "historical-migration-$version"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null).use { old ->
            val sql = instrumentation.context.assets.open("legacy-schemas/$version.sql").bufferedReader().use { it.readText() }
            sql.split(';').filter { it.isNotBlank() }.forEach(old::execSQL)
            old.execSQL("""
                INSERT INTO prompts (_id,title,description,content_ru,content_en,variables_json,
                compatible_models,category,tags,is_local,is_favorite,rating,rating_votes,status,
                author,author_id,source,notes,version,created_at,modified_at)
                VALUES ('historical-personal','Saved prompt',NULL,'Мой текст','Private text','{}',
                '','test','',1,1,0,0,'active','','','','Private note','1','2025-07-02','2025-07-02')
            """.trimIndent())
            if (version >= 2) {
                old.execSQL("INSERT INTO conversations(id,title,lastUpdated) VALUES ('chat','Saved chat',123)")
                old.execSQL("INSERT INTO messages(id,conversationId,role,content,timestamp) VALUES ('message','chat','user','Private chat',123)")
                if (version >= 3) old.execSQL("UPDATE conversations SET systemPrompt='Private system prompt'")
            }
            old.version = version
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
        ).build()
        try {
            val prompt = database.promptDao().getById("historical-personal")!!
            assertEquals("Private text", prompt.contentEn)
            assertEquals("Private note", prompt.notes)
            assertEquals(true, prompt.isLocal)
            assertEquals(true, prompt.isFavorite)
            val db = database.openHelper.writableDatabase // Room validates the actual final schema here.
            if (version >= 2) db.query("SELECT content,attachments_json FROM messages WHERE id='message'").use {
                check(it.moveToFirst()); assertEquals("Private chat", it.getString(0)); assertEquals("[]", it.getString(1))
            }
            if (version >= 3) db.query("SELECT systemPrompt FROM conversations WHERE id='chat'").use {
                check(it.moveToFirst()); assertEquals("Private system prompt", it.getString(0))
            }
            assertEquals(8, db.version)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migrationFrom4To8PreservesPersonalPromptAndFavorite() {
        val name = "migration-test-4-8"
        helper.createDatabase(name, 4).apply {
            execSQL("""
                INSERT INTO prompts (_id,title,description,content_ru,content_en,variables_json,
                compatible_models,category,tags,is_local,is_favorite,rating,rating_votes,status,
                author,author_id,source,notes,version,created_at,modified_at,prompt_variants_json)
                VALUES ('personal','Saved prompt',NULL,'Мой текст','My text','{}',
                '','test','',1,1,0,0,'active','','','','private note','1','2026-09-30','2026-09-30','[]')
            """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8
        ).use { db ->
            db.query("SELECT content_en,is_local,is_favorite,notes FROM prompts WHERE _id='personal'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("My text", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("private note", cursor.getString(3))
            }
        }
    }

    @Test fun catalogTransactionPreservesPersonalContentAndFavorite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.promptDao()
            val personal = PromptEntity(id = "personal", title = "Mine", description = null,
                status = "active", isLocal = true, contentEn = "Private text", notes = "Private note")
            val favorite = personal.copy(id = "public", title = "Public", isLocal = false,
                isFavorite = true, notes = "", contentEn = "Old")
            dao.insertPrompts(listOf(personal, favorite))
            dao.syncPrompts(listOf(
                personal.copy(contentEn = "Remote overwrite", isLocal = false),
                favorite.copy(contentEn = "Updated", isFavorite = false)
            ), listOf(personal.id))
            assertEquals(personal, dao.getById(personal.id))
            assertEquals("Updated", dao.getById(favorite.id)?.contentEn)
            assertEquals(true, dao.getById(favorite.id)?.isFavorite)
        } finally {
            database.close()
        }
    }
}
