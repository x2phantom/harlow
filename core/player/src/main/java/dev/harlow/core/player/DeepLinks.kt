package dev.harlow.core.player

import android.net.Uri
import java.util.UUID

/**
 * Deep links into the media center (DL-2). Browsing and playback have no activity of their own, so a trampoline
 * turns these into the media center's intents:
 *
 * - `harlow://browse/home|library|playlists|favorites` — a tab
 * - `harlow://browse/album|artist|playlist/{id}`, `harlow://browse/genre/{name}` — an album, artist, playlist, genre
 * - `harlow://play/album|artist|playlist|track/{id}` — plays it, then shows the media center
 * - `harlow://search?q={query}` — the media center's search; `&play=1` plays the first result
 * - `harlow://open` — the media center
 *
 * `{id}` is the Jellyfin item id. `harlow://settings` and `harlow://signin` are handled by those activities.
 * The links come from other apps, so anything that doesn't fit these shapes is rejected.
 */
sealed interface DeepLink {
    /** Opens the media center at [mediaId] (a node of the service's content tree). */
    data class Browse(val mediaId: String) : DeepLink

    /** Plays [mediaId] (as understood by the service's `onSetMediaItems`). */
    data class Play(val mediaId: String) : DeepLink

    data class Search(val query: String, val playFirst: Boolean) : DeepLink

    data object Open : DeepLink

    companion object {
        const val SCHEME = "harlow"

        /** Jellyfin item ids: 32 hex digits, with dashes (as stored in Room) or without (as in Jellyfin's URLs). */
        private val ITEM_ID = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        private const val MAX_TEXT_LENGTH = 200

        private val TABS = mapOf(
            "home" to HarlowMediaService.TAB_HOME,
            "library" to HarlowMediaService.TAB_LIBRARY,
            "playlists" to HarlowMediaService.TAB_PLAYLISTS,
            "favorites" to HarlowMediaService.TAB_FAVORITES,
        )

        fun parse(uri: Uri): DeepLink? {
            if (uri.scheme != SCHEME) return null
            val segments = uri.pathSegments
            return when (uri.host) {
                "open" -> Open.takeIf { segments.isEmpty() }
                "browse" -> browseId(segments)?.let(::Browse)
                "play" -> playId(segments)?.let(::Play)
                "search" -> uri.getQueryParameter("q")?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT_LENGTH }
                    ?.let { Search(it, playFirst = uri.getQueryParameter("play") == "1") }
                else -> null
            }
        }

        private fun browseId(segments: List<String>): String? = when (segments.size) {
            1 -> TABS[segments[0]]
            2 -> {
                val (kind, value) = segments
                when (kind) {
                    "album", "artist", "playlist" -> itemId(value)?.let { "$kind:$it" }
                    "genre" -> value.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }?.let { "genre:$it" }
                    else -> null
                }
            }
            else -> null
        }

        /** [value] as the item id Room uses (lowercase, with dashes), or `null` if it isn't one. */
        private fun itemId(value: String): String? {
            if (!ITEM_ID.matches(value)) return null
            val dashed = if (value.length == 32) {
                "${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-" +
                    "${value.substring(16, 20)}-${value.substring(20)}"
            } else {
                value
            }
            return UUID.fromString(dashed).toString()
        }

        private fun playId(segments: List<String>): String? {
            if (segments.size != 2) return null
            val kind = segments[0]
            val id = itemId(segments[1]) ?: return null
            return when (kind) {
                "album", "artist", "playlist" -> "$kind:$id"
                "track" -> id
                else -> null
            }
        }
    }
}
