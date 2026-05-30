package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_history")
data class AlertHistoryEntity(
    @PrimaryKey val alertId: Int,
    val lastNotifiedValue: String,
    val lastNotifiedAtMillis: Long
)
