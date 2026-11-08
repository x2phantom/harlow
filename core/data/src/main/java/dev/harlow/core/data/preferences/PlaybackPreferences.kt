package dev.harlow.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.data.SignOutCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_preferences",
)

@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : SignOutCleanup {
    private val dataStore = context.playbackDataStore

    val streamQuality: Flow<StreamQuality> = dataStore.data.map { preferences ->
        StreamQuality.entries.firstOrNull { it.name == preferences[STREAM_QUALITY] } ?: StreamQuality.DEFAULT
    }

    val autoResume: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_RESUME] ?: false
    }

    suspend fun setStreamQuality(quality: StreamQuality) {
        dataStore.edit { preferences ->
            preferences[STREAM_QUALITY] = quality.name
        }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_RESUME] = enabled
        }
    }

    /** Signing out resets the app as after a fresh install, settings included. */
    override suspend fun clearOnSignOut() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
    }
}
