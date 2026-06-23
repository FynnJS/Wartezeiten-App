package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.wartezeiten.app.data.local.entity.AttractionNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttractionNoteDao {
    @Query("SELECT * FROM attraction_notes WHERE parkKey = :parkKey AND attractionId = :attractionId LIMIT 1")
    fun observeNote(parkKey: String, attractionId: String): Flow<AttractionNoteEntity?>

    @Upsert
    suspend fun upsertNote(note: AttractionNoteEntity)

    @Query("DELETE FROM attraction_notes WHERE parkKey = :parkKey AND attractionId = :attractionId")
    suspend fun deleteNote(parkKey: String, attractionId: String)
}
