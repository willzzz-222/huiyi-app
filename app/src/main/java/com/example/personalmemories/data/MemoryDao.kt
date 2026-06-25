package com.example.personalmemories.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isInvalid = 0 ORDER BY dateTaken DESC")
    suspend fun activeMedia(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE mediaKey = :mediaKey LIMIT 1")
    suspend fun mediaByKey(mediaKey: String): MediaItemEntity?

    @Query("SELECT * FROM media_states WHERE mediaKey = :mediaKey LIMIT 1")
    fun observeState(mediaKey: String): Flow<MediaStateEntity?>

    @Query("SELECT * FROM media_states WHERE mediaKey = :mediaKey LIMIT 1")
    suspend fun stateByKey(mediaKey: String): MediaStateEntity?

    @Query("SELECT * FROM memory_notes WHERE mediaKey = :mediaKey ORDER BY createdAt DESC")
    fun observeNotes(mediaKey: String): Flow<List<MemoryNoteEntity>>

    @Query("SELECT * FROM memory_notes WHERE mediaKey = :mediaKey ORDER BY createdAt DESC")
    suspend fun notesByKey(mediaKey: String): List<MemoryNoteEntity>

    @Query("SELECT * FROM shuffle_sessions WHERE sessionId = 'default' LIMIT 1")
    suspend fun getSession(): ShuffleSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: MediaStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: MemoryNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ShuffleSessionEntity)

    @Query("DELETE FROM memory_notes WHERE noteId = :noteId")
    suspend fun deleteNote(noteId: String)

    @Query("SELECT * FROM memory_notes WHERE mediaKey = :mediaKey AND type = 'AUDIO'")
    suspend fun audioNotesForMedia(mediaKey: String): List<MemoryNoteEntity>

    @Query("DELETE FROM memory_notes WHERE mediaKey = :mediaKey")
    suspend fun deleteNotesForMedia(mediaKey: String)

    @Query("DELETE FROM media_states WHERE mediaKey = :mediaKey")
    suspend fun deleteStateForMedia(mediaKey: String)

    @Query("UPDATE media_items SET isInvalid = 1, lastValidatedAt = :now WHERE mediaKey = :mediaKey")
    suspend fun markInvalid(mediaKey: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM media_items WHERE mediaKey = :mediaKey")
    suspend fun deleteMedia(mediaKey: String)

    @Transaction
    suspend fun removeMediaAndRecords(mediaKey: String) {
        deleteNotesForMedia(mediaKey)
        deleteStateForMedia(mediaKey)
        deleteMedia(mediaKey)
    }
}
