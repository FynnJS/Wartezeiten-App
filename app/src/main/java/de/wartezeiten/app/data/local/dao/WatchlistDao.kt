package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE enabled = 1")
    fun observeActiveWatchlist(): Flow<List<WatchlistEntity>>

    @Insert
    suspend fun insert(item: WatchlistEntity)

    @Query(
        """
        SELECT COUNT(*) FROM watchlist
        WHERE parkKey = :parkKey
          AND type = :type
          AND threshold = :threshold
          AND (
              (:attractionId IS NULL AND attractionId IS NULL)
              OR attractionId = :attractionId
          )
        """
    )
    suspend fun countMatching(
        parkKey: String,
        attractionId: String?,
        type: de.wartezeiten.app.data.local.entity.WatchlistType,
        threshold: Int,
    ): Int

    @Delete
    suspend fun delete(item: WatchlistEntity)

    @Query("UPDATE watchlist SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}
