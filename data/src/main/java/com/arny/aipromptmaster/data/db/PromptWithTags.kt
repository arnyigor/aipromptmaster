package com.arny.aipromptmaster.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.data.db.entities.PromptTagCrossRef
import com.arny.aipromptmaster.data.db.entities.TagEntity

data class PromptWithTags(
    @Embedded
    val prompt: PromptEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(PromptTagCrossRef::class)
    )
    val tags: List<TagEntity>
)
