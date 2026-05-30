package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.wartezeiten.app.data.local.entity.AlertHistoryEntity

@Dao
interface AlertHistoryDao {
    @Query("SELECT * FROM alert_history WHERE alertId = :alertId LIMIT 1")
    suspend fun getHistory(alertId: Int): AlertHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: AlertHistoryEntity)
}
