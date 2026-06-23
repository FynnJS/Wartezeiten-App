package de.wartezeiten.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "attraction_notes",
    primaryKeys = ["parkKey", "attractionId"]
)
data class AttractionNoteEntity(
    val parkKey: String,
    val attractionId: String,
    val note: String,
    val updatedAtMillis: Long,
)
