package dev.harlow.core.player

import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistAliasDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.TrackEntity
import javax.inject.Inject

/** What a voice request ("play X by Y") asks for, see [playSearchRequest]. */
internal data class PlaySearchRequest(
    val query: String,
    val focus: Focus,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val playlist: String? = null,
) {
    enum class Focus { Unstructured, Artist, Album, Track, Genre, Playlist }

    /** "Play some music": no query and nothing structured. */
    val isEmpty: Boolean
        get() = query.isBlank() && title == null && album == null && artist == null && genre == null &&
            playlist == null
}

/**
 * Reads a play-from-search request: the spoken [query] plus the structured `MediaStore` extras the assistant adds
 * (`EXTRA_MEDIA_FOCUS`, `EXTRA_MEDIA_ARTIST`, …), looked up through [extra].
 */
internal fun playSearchRequest(query: String?, extra: (String) -> String?): PlaySearchRequest {
    fun value(key: String) = extra(key)?.trim()?.takeIf { it.isNotEmpty() }
    val focus = when (value(EXTRA_MEDIA_FOCUS)) {
        FOCUS_ARTIST -> PlaySearchRequest.Focus.Artist
        FOCUS_ALBUM -> PlaySearchRequest.Focus.Album
        FOCUS_AUDIO -> PlaySearchRequest.Focus.Track
        FOCUS_GENRE -> PlaySearchRequest.Focus.Genre
        FOCUS_PLAYLIST -> PlaySearchRequest.Focus.Playlist
        else -> PlaySearchRequest.Focus.Unstructured
    }
    return PlaySearchRequest(
        query = query?.trim().orEmpty(),
        focus = focus,
        title = value(EXTRA_MEDIA_TITLE),
        album = value(EXTRA_MEDIA_ALBUM),
        artist = value(EXTRA_MEDIA_ARTIST),
        genre = value(EXTRA_MEDIA_GENRE),
        playlist = value(EXTRA_MEDIA_PLAYLIST),
    )
}

/** Tracks to play for a request, starting at [startIndex]. */
internal data class SearchPlayback(val tracks: List<TrackEntity>, val startIndex: Int = 0)

/**
 * Finds what to play for a voice request in the local library (Room), so it also works offline. [keep] drops tracks
 * that can't play right now (offline: everything that isn't downloaded).
 */
class PlayFromSearchResolver @Inject constructor(
    private val artistDao: ArtistDao,
    private val artistAliasDao: ArtistAliasDao,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
) {

    internal suspend fun resolve(
        serverId: String,
        request: PlaySearchRequest,
        keep: (TrackEntity) -> Boolean,
    ): SearchPlayback? {
        val playback = when {
            request.isEmpty -> anything(serverId)
            request.focus == PlaySearchRequest.Focus.Artist -> artist(serverId, request.artist ?: request.query)
            request.focus == PlaySearchRequest.Focus.Album ->
                album(serverId, request.album ?: request.query, request.artist)
            request.focus == PlaySearchRequest.Focus.Track ->
                track(serverId, request.title ?: request.query, request.artist)
            request.focus == PlaySearchRequest.Focus.Genre -> genre(serverId, request.genre ?: request.query)
            request.focus == PlaySearchRequest.Focus.Playlist -> playlist(serverId, request.playlist ?: request.query)
            else -> unstructured(serverId, request.query)
        } ?: return null
        return playback.keepOnly(keep)
    }

    /** No query: favorites, else what was played lately, shuffled. */
    private suspend fun anything(serverId: String): SearchPlayback? {
        val tracks = trackDao.getFavoriteTracksSync(serverId)
            .ifEmpty { trackDao.getRecentlyPlayedTracks(serverId) }
            .shuffled()
            .take(MAX_TRACKS)
        return tracks.takeIf { it.isNotEmpty() }?.let { SearchPlayback(it) }
    }

    private suspend fun artist(serverId: String, name: String): SearchPlayback? {
        val candidates = artistDao.search(serverId, name, limit = 10)
        val artist = candidates.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: candidates.firstOrNull()
            ?: return null
        val artistId = artistAliasDao.getCanonicalId(serverId, artist.id) ?: artist.id
        val tracks = trackDao.getTracksByResolvedArtistSync(artistId, limit = MAX_TRACKS)
        return tracks.takeIf { it.isNotEmpty() }?.let { SearchPlayback(it) }
    }

    private suspend fun album(serverId: String, name: String, artist: String?): SearchPlayback? {
        val candidates = albumDao.search(serverId, name, limit = 20)
        val album = candidates.bestMatch(name, artist, { it.name }, { it.artistName }) ?: return null
        return albumTracks(album)
    }

    /** The track, followed by the rest of its album. */
    private suspend fun track(serverId: String, title: String, artist: String?): SearchPlayback? {
        val candidates = trackDao.search(serverId, title, limit = 50)
        val track = candidates.bestMatch(title, artist, { it.name }, { it.artistName }) ?: return null
        val album = track.albumId?.let { trackDao.getTracksByAlbumSync(it) }.orEmpty()
        val index = album.indexOfFirst { it.id == track.id }
        return if (index >= 0) SearchPlayback(album, index) else SearchPlayback(listOf(track))
    }

    private suspend fun genre(serverId: String, genre: String): SearchPlayback? {
        val tracks = albumDao.getAlbumsByGenre(genre, serverId)
            .shuffled()
            .take(MAX_GENRE_ALBUMS)
            .flatMap { trackDao.getTracksByAlbumSync(it.id) }
            .shuffled()
            .take(MAX_TRACKS)
        return tracks.takeIf { it.isNotEmpty() }?.let { SearchPlayback(it) }
    }

    /** The playlist in its own order. */
    private suspend fun playlist(serverId: String, name: String): SearchPlayback? {
        val candidates = playlistDao.search(serverId, name, limit = 20)
        val playlist = candidates.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: candidates.firstOrNull()
            ?: return null
        return playlistTracks(playlist.id)
    }

    /**
     * Free text: an artist, album or playlist of exactly that name first, then the best track, album, artist or
     * playlist match.
     */
    private suspend fun unstructured(serverId: String, query: String): SearchPlayback? {
        val artists = artistDao.search(serverId, query, limit = 10)
        val albums = albumDao.search(serverId, query, limit = 20)
        val playlists = playlistDao.search(serverId, query, limit = 20)
        artists.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return artist(serverId, it.name) }
        albums.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return albumTracks(it) }
        playlists.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return playlistTracks(it.id) }
        return track(serverId, query, artist = null)
            ?: albums.firstOrNull()?.let { albumTracks(it) }
            ?: artists.firstOrNull()?.let { artist(serverId, it.name) }
            ?: playlists.firstOrNull()?.let { playlistTracks(it.id) }
    }

    private suspend fun playlistTracks(playlistId: String): SearchPlayback? {
        val tracks = playlistDao.getPlaylistTracksSync(playlistId)
        return tracks.takeIf { it.isNotEmpty() }?.let { SearchPlayback(it) }
    }

    private suspend fun albumTracks(album: AlbumEntity): SearchPlayback? {
        val tracks = trackDao.getTracksByAlbumSync(album.id)
        return tracks.takeIf { it.isNotEmpty() }?.let { SearchPlayback(it) }
    }

    private fun SearchPlayback.keepOnly(keep: (TrackEntity) -> Boolean): SearchPlayback? {
        val start = tracks.getOrNull(startIndex)
        val kept = tracks.filter(keep)
        if (kept.isEmpty()) return null
        return SearchPlayback(kept, kept.indexOf(start).coerceAtLeast(0))
    }

    private companion object {
        const val MAX_TRACKS = 100
        const val MAX_GENRE_ALBUMS = 10
    }
}

/**
 * The candidate whose name equals [name] and whose artist matches [artist], falling back to a name match, an artist
 * match and finally the first candidate.
 */
internal fun <T> List<T>.bestMatch(
    name: String,
    artist: String?,
    nameOf: (T) -> String,
    artistOf: (T) -> String?,
): T? {
    fun T.nameMatches() = nameOf(this).equals(name, ignoreCase = true)
    fun T.artistMatches() = artist != null && artistOf(this)?.contains(artist, ignoreCase = true) == true
    return firstOrNull { it.nameMatches() && it.artistMatches() }
        ?: firstOrNull { it.nameMatches() && artist == null }
        ?: firstOrNull { it.artistMatches() }
        ?: firstOrNull { it.nameMatches() }
        ?: firstOrNull()
}

// android.provider.MediaStore extras of MEDIA_PLAY_FROM_SEARCH, as plain strings so the parsing runs in unit tests.
private const val EXTRA_MEDIA_FOCUS = "android.intent.extra.focus"
private const val EXTRA_MEDIA_TITLE = "android.intent.extra.title"
private const val EXTRA_MEDIA_ALBUM = "android.intent.extra.album"
private const val EXTRA_MEDIA_ARTIST = "android.intent.extra.artist"
private const val EXTRA_MEDIA_GENRE = "android.intent.extra.genre"
private const val EXTRA_MEDIA_PLAYLIST = "android.intent.extra.playlist"
private const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
private const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
private const val FOCUS_AUDIO = "vnd.android.cursor.item/audio"
private const val FOCUS_GENRE = "vnd.android.cursor.item/genre"
private const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"
