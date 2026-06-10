package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parkKey: String,
    val attractionId: String?, // Nullable for park-level alerts
    val type: WatchlistType,
    val threshold: Int
)

enum class WatchlistType {
    WAIT_TIME_BELOW,
    WAIT_TIME_ABOVE,
    NOW_OPENED,
    CROWD_LEVEL_BELOW,
    CROWD_LEVEL_ABOVE,
    ATTRACTION_STATUS_CHANGE,
    ATTRACTION_OPEN,
    ATTRACTION_CLOSED,
    ATTRACTION_MAINTENANCE,
    PARK_STATUS_CHANGED,
    PARK_ALL_CHANGES,
    ATTRACTION_ALL_CHANGES
}
