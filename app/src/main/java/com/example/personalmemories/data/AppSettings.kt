package com.example.personalmemories.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

data class AppSettings(
    val autoplayVideo: Boolean = true,
    val muteVideoByDefault: Boolean = true,
    val continueLastPosition: Boolean = true,
    val hasSeenIntro: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private val autoplayVideo = booleanPreferencesKey("autoplayVideo")
    private val muteVideoByDefault = booleanPreferencesKey("muteVideoByDefault")
    private val continueLastPosition = booleanPreferencesKey("continueLastPosition")
    private val hasSeenIntro = booleanPreferencesKey("hasSeenIntro")

    val settings: Flow<AppSettings> = context.settingsStore.data.map {
        AppSettings(
            autoplayVideo = it[autoplayVideo] ?: true,
            muteVideoByDefault = it[muteVideoByDefault] ?: true,
            continueLastPosition = it[continueLastPosition] ?: true,
            hasSeenIntro = it[hasSeenIntro] ?: false
        )
    }

    suspend fun markIntroSeen() {
        context.settingsStore.edit { it[hasSeenIntro] = true }
    }
}
