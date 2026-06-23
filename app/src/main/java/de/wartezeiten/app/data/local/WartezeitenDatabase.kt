package de.wartezeiten.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.ParkDetailDao
import de.wartezeiten.app.data.local.dao.ParkSnapshotDao
import de.wartezeiten.app.data.local.entity.CrowdLevelEntity
import de.wartezeiten.app.data.local.entity.OpeningTimesEntity
import de.wartezeiten.app.data.local.entity.ParkEntity
import de.wartezeiten.app.data.local.entity.ParkSnapshotEntity
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistTypeConverter
import de.wartezeiten.app.data.local.entity.WaitingTimeEntity
import de.wartezeiten.app.data.local.entity.WeatherEntity
import de.wartezeiten.app.data.local.entity.WeatherForecastEntity
import de.wartezeiten.app.data.local.entity.HolidayEntity
import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.AttractionNoteEntity

@Database(
    entities = [
        ParkEntity::class,
        OpeningTimesEntity::class,
        WaitingTimeEntity::class,
        CrowdLevelEntity::class,
        ParkSnapshotEntity::class,
        WatchlistEntity::class,
        WeatherEntity::class,
        WeatherForecastEntity::class,
        HolidayEntity::class,
        AlertHistoryEntity::class,
        AttractionNoteEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(WatchlistTypeConverter::class)
abstract class WartezeitenDatabase : RoomDatabase() {
    abstract fun parkDao(): ParkDao
    abstract fun parkDetailDao(): ParkDetailDao
    abstract fun parkSnapshotDao(): ParkSnapshotDao
    abstract fun watchlistDao(): de.wartezeiten.app.data.local.dao.WatchlistDao
    abstract fun alertHistoryDao(): de.wartezeiten.app.data.local.dao.AlertHistoryDao
    abstract fun attractionNoteDao(): de.wartezeiten.app.data.local.dao.AttractionNoteDao
}
