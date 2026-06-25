package com.example.personalmemories.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType { PHOTO, VIDEO }
enum class NoteType { TEXT, AUDIO }

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val mediaKey: String,
    val contentUri: String,
    val type: MediaType,
    val dateTaken: Long,
    val dateAdded: Long,
    val albumId: String?,
    val albumName: String?,
    val displayName: String?,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val isHidden: Boolean = false,
    val isInvalid: Boolean = false,
    val lastValidatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_states")
data class MediaStateEntity(
    @PrimaryKey val mediaKey: String,
    val isLiked: Boolean = false,
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val lastSeenAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_notes")
data class MemoryNoteEntity(
    @PrimaryKey val noteId: String,
    val mediaKey: String,
    val type: NoteType,
    val text: String = "",
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shuffle_sessions")
data class ShuffleSessionEntity(
    @PrimaryKey val sessionId: String = "default",
    val filterSnapshot: String = "all",
    val orderedMediaKeys: String,
    val currentIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
