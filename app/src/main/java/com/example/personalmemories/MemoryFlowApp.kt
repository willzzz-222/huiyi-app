package com.example.personalmemories

import android.app.Application
import com.example.personalmemories.data.AppDatabase
import com.example.personalmemories.data.SettingsRepository
import com.example.personalmemories.media.AudioCoordinator
import com.example.personalmemories.media.MediaScanner

class MemoryFlowApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val scanner by lazy { MediaScanner(this) }
    val settings by lazy { SettingsRepository(this) }
    val audio by lazy { AudioCoordinator(this) }
}
