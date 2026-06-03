package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.wartezeiten.app.data.local.entity.ParkSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ParkSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ParkSnapshotEntity>)

    @Query("SELECT * FROM park_snapshots WHERE parkKey = :parkKey ORDER BY capturedAtMillis DESC")
    fun getSnapshotsByParkKey(parkKey: String): Flow<List<ParkSnapshotEntity>>

    @Query(
        """
        SELECT snapshots.parkKey
        FROM park_snapshots AS snapshots
        INNER JOIN (
            SELECT parkKey, MAX(capturedAtMillis) AS latestCapture
            FROM park_snapshots
            GROUP BY parkKey
        ) AS latest
            ON snapshots.parkKey = latest.parkKey
            AND snapshots.capturedAtMillis = latest.latestCapture
        WHERE snapshots.openedToday = 1
        """
    )
    fun observeLatestOpenParkKeys(): Flow<List<String>>

    @Query(
        """
        SELECT snapshots.*
        FROM park_snapshots AS snapshots
        INNER JOIN (
            SELECT parkKey, MAX(capturedAtMillis) AS latestCapture
            FROM park_snapshots
            GROUP BY parkKey
        ) AS latest
            ON snapshots.parkKey = latest.parkKey
            AND snapshots.capturedAtMillis = latest.latestCapture
        """
    )
    fun observeLatestSnapshots(): Flow<List<ParkSnapshotEntity>>
    
    @Query("DELETE FROM park_snapshots WHERE capturedAtMillis < :olderThanMillis")
    suspend fun deleteOldSnapshots(olderThanMillis: Long)
}
