package com.example.personalmemories.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.personalmemories.data.MediaItemEntity
import com.example.personalmemories.data.MediaType

class MediaScanner(private val context: Context) {
    suspend fun scan(): List<MediaItemEntity> {
        val items = mutableListOf<MediaItemEntity>()
        loadImages(context.contentResolver, items)
        loadVideos(context.contentResolver, items)
        return items
    }

    private fun loadImages(resolver: ContentResolver, out: MutableList<MediaItemEntity>) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE
        )
        query(resolver, uri, projection, MediaType.PHOTO, out)
    }

    private fun loadVideos(resolver: ContentResolver, out: MutableList<MediaItemEntity>) {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        query(resolver, uri, projection, MediaType.VIDEO, out)
    }

    private fun query(
        resolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        type: MediaType,
        out: MutableList<MediaItemEntity>
    ) {
        try {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.long(MediaStore.MediaColumns._ID)
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        val dateTaken = cursor.longOrZero(MediaStore.Images.Media.DATE_TAKEN)
                        val dateAdded = cursor.longOrZero(MediaStore.MediaColumns.DATE_ADDED) * 1000L
                        val displayName = cursor.stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
                        val size = cursor.longOrZero(MediaStore.MediaColumns.SIZE)
                        out += MediaItemEntity(
                            mediaKey = "${type.name}:$id",
                            contentUri = contentUri,
                            type = type,
                            dateTaken = if (dateTaken > 0) dateTaken else dateAdded,
                            dateAdded = dateAdded,
                            albumId = cursor.stringOrNull("bucket_id"),
                            albumName = cursor.stringOrNull("bucket_display_name"),
                            displayName = displayName,
                            mimeType = cursor.stringOrNull(MediaStore.MediaColumns.MIME_TYPE),
                            width = cursor.intOrZero(MediaStore.MediaColumns.WIDTH),
                            height = cursor.intOrZero(MediaStore.MediaColumns.HEIGHT),
                            durationMs = if (type == MediaType.VIDEO) cursor.longOrZero(MediaStore.Video.Media.DURATION) else 0L,
                            sizeBytes = size
                        )
                    }
                }
        } catch (_: SecurityException) {
        }
    }
}

private fun Cursor.index(name: String): Int = getColumnIndex(name)
private fun Cursor.stringOrNull(name: String): String? = index(name).takeIf { it >= 0 }?.let { getString(it) }
private fun Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))
private fun Cursor.longOrZero(name: String): Long = index(name).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L
private fun Cursor.intOrZero(name: String): Int = index(name).takeIf { it >= 0 }?.let { getInt(it) } ?: 0
