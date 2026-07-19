package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.wartezeiten.app.data.local.entity.ParkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkDao {
    @Query(
        """
        SELECT * FROM parks
        WHERE :query IS NULL
            OR name LIKE '%' || :query || '%'
            OR country LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeParks(query: String?): Flow<List<ParkEntity>>

    @Query("SELECT * FROM parks WHERE id = :parkKey OR uuid = :parkKey LIMIT 1")
    fun observePark(parkKey: String): Flow<ParkEntity?>

    @Upsert
    suspend fun upsertParks(parks: List<ParkEntity>)

    @Query("UPDATE parks SET isFavorite = :isFavorite WHERE id = :parkId")
    suspend fun updateFavorite(parkId: String, isFavorite: Boolean)

    @Query("DELETE FROM parks WHERE id = :parkId OR uuid = :parkId")
    suspend fun deletePark(parkId: String)

    @Query("DELETE FROM parks WHERE isFavorite = 0")
    suspend fun deleteNonFavoriteParks()
}
