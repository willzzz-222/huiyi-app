package com.example.personalmemories.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.name
    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
    @TypeConverter fun noteTypeToString(value: NoteType): String = value.name
    @TypeConverter fun stringToNoteType(value: String): NoteType = NoteType.valueOf(value)
}
