package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crowd_levels")
data class CrowdLevelEntity(
    @PrimaryKey val parkKey: String,
    val crowdLevel: Float?,
    val timestamp: String?,
    val updatedAtMillis: Long
)
