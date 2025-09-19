import com.arny.aiprompts.data.db.entities.PromptEntity
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.mappers.toEntity
import com.arny.aiprompts.data.mappers.toPromptJson
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.model.PromptMetadata
import com.arny.aiprompts.data.model.Rating
import com.arny.aiprompts.domain.model.*
import com.benasher44.uuid.uuid4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class PromptExtensionsTest {

    @Test
    fun `Prompt toEntity maps correctly`() {
        val prompt = createTestPrompt()
        val entity = prompt.toEntity()

        assertEquals(prompt.id, entity.id)
        assertEquals(prompt.title, entity.title)
        assertEquals(prompt.content?.ru.orEmpty(), entity.contentRu)
        assertEquals(prompt.content?.en.orEmpty(), entity.contentEn)
        assertEquals(prompt.description, entity.description)
        assertEquals(prompt.category, entity.category)
        assertEquals(prompt.tags.joinToString(","), entity.tags)
        assertEquals(prompt.isLocal, entity.isLocal)
        assertEquals(prompt.isFavorite, entity.isFavorite)
        assertEquals(prompt.rating, entity.rating)
        assertEquals(prompt.ratingVotes, entity.ratingVotes)
        assertEquals(prompt.compatibleModels.joinToString(), entity.compatibleModels)
        assertEquals(prompt.metadata.author?.name.orEmpty(), entity.author)
        assertEquals(prompt.metadata.source.orEmpty(), entity.source)
        assertEquals(prompt.createdAt?.toString().orEmpty(), entity.createdAt)
    }

    @Test
    fun `PromptEntity toDomain maps correctly`() {
        val entity = createTestPromptEntity()
        val prompt = entity.toDomain()

        assertEquals(entity.id, prompt.id)
        assertEquals(entity.title, prompt.title)
        assertEquals(entity.contentRu, prompt.content?.ru)
        assertEquals(entity.contentEn, prompt.content?.en)
        assertEquals(entity.description, prompt.description)
        assertEquals(entity.category, prompt.category)
        assertEquals(entity.tags.split(","), prompt.tags)
        assertEquals(entity.isLocal, prompt.isLocal)
        assertEquals(entity.isFavorite, prompt.isFavorite)
        assertEquals(entity.rating, prompt.rating)
        assertEquals(entity.ratingVotes, prompt.ratingVotes)
        assertEquals(entity.compatibleModels.split(","), prompt.compatibleModels)
        assertEquals(entity.author, prompt.metadata.author?.name)
        assertEquals(entity.source, prompt.metadata.source)
        assertNotNull(prompt.createdAt)
        assertNotNull(prompt.modifiedAt)
    }

    @Test
    fun `PromptJson toDomain maps correctly with null id generates uuid`() {
        val json = createTestPromptJson()
        val prompt = json.toDomain()

        assertNotNull(prompt.id)
        assertEquals(json.title.orEmpty(), prompt.title)
        assertEquals(json.content["ru"].orEmpty(), prompt.content?.ru)
        assertEquals(json.content["en"].orEmpty(), prompt.content?.en)
        assertEquals(json.category.orEmpty().lowercase(), prompt.category)
        assertEquals(json.tags, prompt.tags)
        assertEquals(json.isLocal, prompt.isLocal)
        assertEquals(json.isFavorite, prompt.isFavorite)
        assertEquals(json.rating?.score ?: 0.0f, prompt.rating)
        assertEquals(json.status.orEmpty().lowercase(), prompt.status)
    }

    @Test
    fun `PromptData toPromptJson maps correctly`() {
        val promptData = createTestPromptData()
        val json = promptData.toPromptJson()

        assertEquals(promptData.id, json.id)
        assertEquals(promptData.sourceId, json.sourceId)
        assertEquals(promptData.title, json.title)
        assertEquals("1.0.0", json.version)
        assertEquals("active", json.status)
        assertEquals(false, json.isLocal)
        assertEquals(false, json.isFavorite)
        assertEquals(promptData.description, json.description)
        assertEquals(promptData.variants.firstOrNull()?.content ?: "", json.content["ru"])
        assertEquals(promptData.category, json.category)
        assertEquals(promptData.tags, json.tags)
        assertEquals(promptData.author.name, json.metadata?.author?.name)
        assertEquals("Импортировано из поста ${promptData.sourceId}", json.metadata?.notes)
    }

    private fun createTestPrompt(): Prompt {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return Prompt(
            id = "test-id",
            title = "Test Prompt",
            content = PromptContent(ru = "Русский контент", en = "English content"),
            description = "Test description",
            category = "test",
            status = "active",
            tags = listOf("tag1", "tag2"),
            isLocal = true,
            isFavorite = false,
            rating = 4.5f,
            ratingVotes = 10,
            compatibleModels = listOf("gpt-4", "claude"),
            metadata = PromptMetadata(
                author = Author(id = "author-id", name = "Test Author"),
                source = "test-source",
                notes = "Test notes"
            ),
            version = "1.0.0",
            createdAt = now,
            modifiedAt = now
        )
    }

    private fun createTestPromptEntity(): PromptEntity {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return PromptEntity(
            id = "test-id",
            title = "Test Prompt",
            contentRu = "Русский контент",
            contentEn = "English content",
            description = "Test description",
            category = "test",
            status = "active",
            tags = "tag1,tag2",
            isLocal = true,
            isFavorite = false,
            rating = 4.5f,
            ratingVotes = 10,
            compatibleModels = "gpt-4,claude",
            author = "Test Author",
            authorId = "author-id",
            source = "test-source",
            notes = "Test notes",
            version = "1.0.0",
            createdAt = now.toString(),
            modifiedAt = now.toString()
        )
    }

    private fun createTestPromptJson(): PromptJson {
        return PromptJson(
            id = null, // Test UUID generation
            title = "Test Prompt",
            description = "Test description",
            content = mapOf("ru" to "Русский контент", "en" to "English content"),
            variables = emptyList(),
            compatibleModels = listOf("gpt-4"),
            category = "Test",
            tags = listOf("tag1"),
            isLocal = true,
            isFavorite = false,
            rating = Rating(score = 4.5f, votes = 10),
            status = "Active",
            metadata = PromptMetadata(
                author = Author(id = "author-id", name = "Test Author"),
                source = "test-source",
                notes = "Test notes"
            ),
            version = "1.0.0",
            createdAt = "2023-01-01T12:00:00",
            updatedAt = "2023-01-01T12:00:00"
        )
    }

    private fun createTestPromptData(): PromptData {
        val now = kotlinx.datetime.Clock.System.now()
        return PromptData(
            id = "test-id",
            sourceId = "post-123",
            title = "Test Prompt Data",
            description = "Test description",
            variants = listOf(PromptVariant(content = "Test content")),
            author = Author(id = "author-id", name = "Test Author"),
            createdAt = now.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
            category = "test",
            tags = listOf("tag1", "tag2"),
            source = "test-source"
        )
    }
}