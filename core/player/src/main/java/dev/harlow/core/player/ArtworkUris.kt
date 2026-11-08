package dev.harlow.core.player

import android.net.Uri
import androidx.annotation.DrawableRes

/**
 * The cover the car's media center shows for an item without artwork (MC-1: every item has a thumbnail): one look for
 * all — the white icon of the item's kind on a grey tile, rendered by [ArtworkProvider].
 */
enum class ArtworkPlaceholder(val pathName: String, @DrawableRes val icon: Int) {
    MUSIC("music", R.drawable.ic_aa_music_note),
    ALBUM("album", R.drawable.ic_aa_albums),
    ARTIST("artist", R.drawable.ic_aa_artists),
    PLAYLIST("playlist", R.drawable.ic_aa_playlists),
    ;

    companion object {
        fun fromPathName(name: String?): ArtworkPlaceholder? = entries.firstOrNull { it.pathName == name }
    }
}

/**
 * `content://` URIs of [ArtworkProvider] (`authority` = `${packageName}.artwork`):
 * - `content://<authority>/<itemId>?placeholder=<kind>&v=<imageTag>`: the item's artwork, [placeholder] if it can't
 *   be had
 * - `content://<authority>/placeholder/<kind>`: only the placeholder, for items known to have no artwork
 *
 * `v` is the image tag, which Jellyfin changes with the image: the media center keeps loaded artwork by URI, so a
 * replaced cover needs a new URI. [ArtworkProvider] doesn't read it, it serves the current image of the item.
 */
object ArtworkUris {

    private const val PLACEHOLDER = "placeholder"
    private const val VERSION = "v"

    /** The artwork of [itemId] if it has some ([imageTag] not null), otherwise straight the placeholder. */
    fun forItem(authority: String, itemId: String, imageTag: String?, placeholder: ArtworkPlaceholder): Uri =
        if (imageTag != null) item(authority, itemId, placeholder, imageTag) else placeholder(authority, placeholder)

    /** The artwork of [itemId], in the version of [imageTag] (null: unknown, e.g. not synced yet). */
    fun item(authority: String, itemId: String, placeholder: ArtworkPlaceholder, imageTag: String? = null): Uri =
        base(authority).appendPath(itemId).appendQueryParameter(PLACEHOLDER, placeholder.pathName)
            .apply { imageTag?.let { appendQueryParameter(VERSION, it) } }
            .build()

    fun placeholder(authority: String, placeholder: ArtworkPlaceholder): Uri =
        base(authority).appendPath(PLACEHOLDER).appendPath(placeholder.pathName).build()

    /** The item id [uri] asks for, or null if it asks only for a placeholder (or for nothing valid). */
    fun itemId(uri: Uri): String? = uri.pathSegments.singleOrNull()

    /** The placeholder for [uri]: the one it names, otherwise (older URIs, unknown kind) the music note. */
    fun placeholderOf(uri: Uri): ArtworkPlaceholder {
        val segments = uri.pathSegments
        val name = if (segments.size == 2 && segments[0] == PLACEHOLDER) {
            segments[1]
        } else {
            uri.getQueryParameter(PLACEHOLDER)
        }
        return ArtworkPlaceholder.fromPathName(name) ?: ArtworkPlaceholder.MUSIC
    }

    private fun base(authority: String) = Uri.Builder().scheme("content").authority(authority)
}
