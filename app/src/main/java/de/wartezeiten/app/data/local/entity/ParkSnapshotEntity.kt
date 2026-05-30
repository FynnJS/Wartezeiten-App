package de.wartezeiten.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "park_snapshots",
    primaryKeys = ["parkKey", "capturedAtMillis"],
)
data class ParkSnapshotEntity(
    val parkKey: String,
    val capturedAtMillis: Long,
    val apiCrowdLevel: Float?,
    val calculatedCrowdLevel: Float?,
    val displayCrowdLevel: Float?,
    val openedToday: Boolean?,
    val openFrom: String?,
    val closedFrom: String?,
    val openAttractions: Int,
    val totalAttractions: Int,
)
