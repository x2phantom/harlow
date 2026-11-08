package dev.harlow.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.harlow.core.data.SignOutCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "display_preferences",
)

/** How the media center shows the library. */
@Singleton
class DisplayPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : SignOutCleanup {
    private val dataStore = context.displayDataStore

    /** Albums, artists, songs, genres and playlists as a list with small covers instead of a cover grid. */
    val listView: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LIST_VIEW] ?: false
    }

    suspend fun setListView(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LIST_VIEW] = enabled
        }
    }

    /** Signing out resets the app as after a fresh install, settings included. */
    override suspend fun clearOnSignOut() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val LIST_VIEW = booleanPreferencesKey("list_view")
    }
}
