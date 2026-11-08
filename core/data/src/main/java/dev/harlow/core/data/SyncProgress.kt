package dev.harlow.core.data

import android.content.Context

data class SyncProgress(
    val phase: String,
    val current: Int,
    val total: Int,
) {
    /** [phase] as shown to the user. */
    fun phaseLabel(context: Context): String = when (phase) {
        "home" -> context.getString(R.string.sync_phase_home)
        "favorites" -> context.getString(R.string.sync_phase_favorites)
        "albums" -> context.getString(R.string.sync_phase_albums)
        "artists" -> context.getString(R.string.sync_phase_artists)
        "tracks" -> context.getString(R.string.sync_phase_tracks)
        "playlists" -> context.getString(R.string.sync_phase_playlists)
        "artwork" -> context.getString(R.string.sync_phase_artwork)
        else -> phase
    }
}
