package dev.harlow.core.player

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SimpleBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.harlow.core.common.HarlowResult
import dev.harlow.core.common.PlaybackReporter
import dev.harlow.core.common.StreamQuality
import dev.harlow.core.common.jellyfinStreamUrl
import dev.harlow.core.data.ActiveSession
import dev.harlow.core.data.preferences.DisplayPreferences
import dev.harlow.core.data.preferences.PlaybackPreferences
import dev.harlow.core.data.preferences.PlaybackQueuePreferences
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.data.repository.UserRepository
import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistAliasDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.DownloadDao
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.dao.getTracksById
import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.ArtistEntity
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.TrackEntity
import dev.harlow.core.network.ConnectionState
import dev.harlow.core.network.JellyfinClientWrapper
import dev.harlow.core.network.NetworkStateObserver
import dev.harlow.core.player.cache.CachedTracks
import dev.harlow.core.player.cache.HarlowCache
import dev.harlow.core.player.cache.HarlowDataSourceFactory
import dev.harlow.core.player.cache.PlayerSignOutCleanup
import dev.harlow.core.player.cache.StreamSource
import dev.harlow.core.player.cache.streamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class HarlowMediaService : MediaLibraryService() {

    @Inject lateinit var dataSourceFactory: HarlowDataSourceFactory
    @Inject lateinit var harlowCache: HarlowCache
    @Inject lateinit var serverDao: ServerDao
    @Inject lateinit var albumDao: AlbumDao
    @Inject lateinit var artistDao: ArtistDao
    @Inject lateinit var artistAliasDao: ArtistAliasDao
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var networkStateObserver: NetworkStateObserver
    @Inject lateinit var jellyfinClientWrapper: JellyfinClientWrapper
    @Inject lateinit var queuePreferences: PlaybackQueuePreferences
    @Inject lateinit var playbackPreferences: PlaybackPreferences
    @Inject lateinit var displayPreferences: DisplayPreferences
    @Inject lateinit var playbackReporter: PlaybackReporter
    @Inject lateinit var signInIntentProvider: SignInIntentProvider
    @Inject lateinit var playFromSearchResolver: PlayFromSearchResolver
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var syncPreferences: SyncPreferences
    @Inject lateinit var activeSession: ActiveSession
    @Inject lateinit var playerSignOutCleanup: PlayerSignOutCleanup

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var playbackReporting: PlaybackReporting? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Album id → image tag, for the artwork URIs of tracks (see [trackArtworkUri]). */
    private val albumImageTags = MutableStateFlow<Map<String, String>>(emptyMap())

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The server the player's tracks come from, kept current so a queue is tagged with its server when it changes
     * rather than whenever the save runs.
     */
    @Volatile private var activeServerId: String? = null

    private val firstConnectionCheck = CompletableDeferred<Unit>()

    /** The current track, whose favorite state (and its album's and artist's) the media center's buttons show. */
    private val currentTrackId = MutableStateFlow<String?>(null)

    /** Favorite state of [currentTrackId], its album and artist in Room. Main thread only. */
    private var currentFavorites = MediaCenterActions.Favorites.UNKNOWN

    /** Tracks whose favorite changed while they played; see [resyncFavorite]. */
    private val favoritesChangedWhilePlaying = ConcurrentHashMap.newKeySet<String>()

    /** Parents the media center has browsed; told to reload when the sign-in or connection state changes. */
    private val browsedParents = ConcurrentHashMap.newKeySet<String>()

    /**
     * How the media center lays out albums, artists, songs, genres and playlists: a cover grid or a list with small
     * covers (the list-view setting). Read again on every browse, so the first browse already follows the setting.
     */
    @Volatile private var collectionStyle = CONTENT_STYLE_GRID_ITEM

    private suspend fun refreshCollectionStyle() {
        collectionStyle = collectionStyleOf(displayPreferences.listView.first())
    }
    private val savePositionPeriodically = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        if (mediaLibrarySession != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheDataSourceFactory = dataSourceFactory.createPlaybackDataSourceFactory()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                fallBackToOriginal(exoPlayer, error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "Playback state: $stateName, items=${exoPlayer.mediaItemCount}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TAG, "Media item transition: id=${mediaItem?.mediaId}")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d(TAG, "playWhenReady=$playWhenReady reason=$reason")
            }
        })

        exoPlayer.addListener(queuePersistenceListener)
        exoPlayer.addListener(mediaButtonsListener)
        exoPlayer.addListener(ReleaseFocusAtEnd(exoPlayer, Handler(exoPlayer.applicationLooper)))
        exoPlayer.addListener(
            AudioFocusDenialListener {
                Log.i(TAG, "Audio focus denied, not playing")
                showNotice(SessionError.ERROR_INVALID_STATE, R.string.playback_error_audio_focus_denied)
            },
        )
        playbackReporting = PlaybackReporting(exoPlayer, playbackReporter, serviceScope, ::resyncFavorite)
            .also { it.attach() }

        val bitmapLoader = CacheBitmapLoader(ContentBitmapLoader(this))

        // The session sees errors with readable messages; the service keeps using exoPlayer directly.
        // Library errors (signed out, see signInRequired) become the platform session's playback state error, which
        // is how the car's media center learns to show its "Sign in" button.
        mediaLibrarySession = MediaLibrarySession.Builder(this, FriendlyErrorPlayer(exoPlayer, this), LibrarySessionCallback())
            .setBitmapLoader(bitmapLoader)
            .setLibraryErrorReplicationMode(MediaLibrarySession.LIBRARY_ERROR_REPLICATION_MODE_FATAL)
            .setMediaButtonPreferences(mediaButtons(exoPlayer))
            .build()

        Log.d(TAG, "MediaLibrarySession created")

        serviceScope.launch {
            if (!jellyfinClientWrapper.isConnected) {
                if (activeSession.ensureConnected() == null) {
                    Log.w(TAG, "No active server in Room, skipping session restore")
                    return@launch
                }
                Log.d(TAG, "Jellyfin session restored")
                networkStateObserver.refresh()
            }
        }

        serviceScope.launch {
            var first = true
            serverDao.observeActiveServer().map { it?.id }.distinctUntilChanged().collect { serverId ->
                activeServerId = serverId
                if (!first) withContext(Dispatchers.Main) { onActiveServerChanged(serverId) }
                // Started signed out: nothing streamed before may stay (e.g. the app was killed while signing out).
                if (first && serverId == null) clearStreamingCacheWhenSettled()
                first = false
            }
        }

        // Right after signing in the library is empty, and the media center has already shown that; it browses again
        // once the first sync brought albums (the home rows come first), and whenever a sync finished.
        serviceScope.launch {
            serverDao.observeActiveServer().map { it?.id }.distinctUntilChanged()
                .flatMapLatest { serverId ->
                    if (serverId == null) flowOf(false) else albumDao.observeHasAlbums(serverId)
                }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { withContext(Dispatchers.Main) { notifyLibraryChanged() } }
        }
        serviceScope.launch {
            syncPreferences.lastSyncTimestamp.distinctUntilChanged().drop(1)
                .collect { withContext(Dispatchers.Main) { notifyLibraryChanged() } }
        }

        serviceScope.launch {
            albumDao.observeImageTags().collect { tags -> albumImageTags.value = tags.associate { it.id to it.imageTag } }
        }

        serviceScope.launch {
            playbackPreferences.streamQuality.distinctUntilChanged().drop(1).collect { applyStreamQuality() }
        }

        // The list-view setting changes the hints on the folders, so the media center has to browse again.
        serviceScope.launch {
            displayPreferences.listView.distinctUntilChanged().drop(1).collect { listView ->
                collectionStyle = collectionStyleOf(listView)
                withContext(Dispatchers.Main) { notifyLibraryChanged() }
            }
        }

        // The favorite buttons follow the favorite state of the current track, its album and artist, wherever it
        // gets changed.
        serviceScope.launch {
            currentTrackId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null to MediaCenterActions.Favorites.UNKNOWN)
                    } else {
                        observeFavorites(id).map { id to it }
                    }
                }
                .distinctUntilChanged()
                .collect { (trackId, favorites) ->
                    withContext(Dispatchers.Main) {
                        if (trackId != currentTrackId.value) return@withContext
                        currentFavorites = favorites
                        updateMediaButtons()
                    }
                }
        }

        // Offline, only downloads and tracks completely in the streaming cache are listed. The state is only known
        // once the server answered, which is usually after the media center's first browse, so it browses again
        // whenever the state changes.
        serviceScope.launch {
            var first = true
            networkStateObserver.connectionState.map { it == ConnectionState.Connected }.distinctUntilChanged()
                .collect { connected ->
                    Log.i(TAG, "Server connection changed, connected=$connected")
                    if (!first) withContext(Dispatchers.Main) { notifyLibraryChanged() }
                    first = false
                }
        }
    }

    /**
     * Signed in, out (in the app or in the system's account settings) or switched servers: the old queue can't play
     * anymore, and the media center has to browse again, which also clears or sets the sign-in error.
     */
    private fun onActiveServerChanged(serverId: String?) {
        val session = mediaLibrarySession ?: return
        Log.i(TAG, "Active server changed, signedIn=${serverId != null}")
        player?.run {
            stop()
            clearMediaItems()
        }
        if (serverId == null) clearStreamingCacheWhenSettled()
        if (serverId != null) session.clearReplicatedLibraryError()
        notifyLibraryChanged()
    }

    /**
     * Signed out: signing out cleared the streaming cache, but ExoPlayer stops asynchronously and its loader can still
     * finish a piece of the last track afterwards. Clear again once the player has settled.
     */
    private fun clearStreamingCacheWhenSettled() {
        serviceScope.launch {
            delay(STREAMING_CACHE_SETTLE_MS)
            playerSignOutCleanup.clearStreamingCache()
        }
    }

    private fun notifyLibraryChanged() {
        val session = mediaLibrarySession ?: return
        (listOf(ROOT_ID) + ROOT_TABS + browsedParents).toSet().forEach { parentId ->
            session.notifyChildrenChanged(parentId, Int.MAX_VALUE, null)
        }
    }

    /**
     * Tells the media center that nobody is signed in. With error replication (see onCreate) this becomes the session's
     * `ERROR_CODE_AUTHENTICATION_EXPIRED` playback state, with a "Sign in" button that opens the sign-in activity.
     */
    private fun <T : Any> signInRequired(): LibraryResult<T> {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            signInIntentProvider.signInIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val extras = Bundle().apply {
            putString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT, getString(R.string.sign_in_action))
            putParcelable(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT, pendingIntent)
        }
        return LibraryResult.ofError<T>(
            SessionError(SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED, getString(R.string.sign_in_required), extras),
        )
    }

    /** Saves the play queue as it changes, so [restoreQueue] can bring it back in a new service. */
    private val queuePersistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePosition()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) savePosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(savePositionPeriodically)
            if (isPlaying) {
                handler.postDelayed(savePositionPeriodically, POSITION_SAVE_INTERVAL_MS)
            } else {
                savePosition()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveQueue()

        override fun onRepeatModeChanged(repeatMode: Int) = saveQueue()

        // Also false when something else took the audio focus: better stay paused than cut in on another source.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            queuePreferences.savePlaying(playWhenReady)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) queuePreferences.savePlaying(false)
        }
    }

    /** Keeps the media center's custom actions (see [MediaCenterActions]) in step with the player. */
    private val mediaButtonsListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                // The favorite button keeps the previous track's state until Room answered for this one, so the
                // buttons don't jump around between tracks.
                currentTrackId.value = player.currentMediaItem?.mediaId
                updateMediaButtons()
            }
        }
    }

    private fun mediaButtons(exoPlayer: Player): List<CommandButton> =
        MediaCenterActions.buttons(
            labels = MediaCenterActions.Labels(
                addFavorite = getString(R.string.action_add_favorite),
                removeFavorite = getString(R.string.action_remove_favorite),
                addAlbumFavorite = getString(R.string.action_add_album_favorite),
                removeAlbumFavorite = getString(R.string.action_remove_album_favorite),
                addArtistFavorite = getString(R.string.action_add_artist_favorite),
                removeArtistFavorite = getString(R.string.action_remove_artist_favorite),
                shuffleOn = getString(R.string.action_shuffle_on),
                shuffleOff = getString(R.string.action_shuffle_off),
                repeatOff = getString(R.string.action_repeat_off),
                repeatAll = getString(R.string.action_repeat_all),
                repeatOne = getString(R.string.action_repeat_one),
            ),
            hasCurrentItem = exoPlayer.currentMediaItem != null,
            favorites = currentFavorites,
            shuffleEnabled = exoPlayer.shuffleModeEnabled,
            repeatMode = exoPlayer.repeatMode,
        )

    private fun updateMediaButtons() {
        val exoPlayer = player ?: return
        mediaLibrarySession?.setMediaButtonPreferences(mediaButtons(exoPlayer))
    }

    /**
     * Tells the controllers why an action failed, as a non-fatal session error. Of the media centers seen so far, the
     * AOSP one of Android 13 ignores it and that of Android 15 shows it as a toast only until Media3 clears the error a
     * moment later; a toast of the app's own isn't shown for a background app. Main thread.
     */
    private fun showNotice(errorCode: Int, @StringRes message: Int) {
        mediaLibrarySession?.sendError(SessionError(errorCode, getString(message)))
    }

    /**
     * Favorite state of a track, its album and its artist (the canonical one of its aliases, as listed in the
     * library). Only the parts of the track that matter, so play counts changing during playback don't re-query.
     */
    private fun observeFavorites(trackId: String): Flow<MediaCenterActions.Favorites> =
        trackDao.observeTrackById(trackId)
            .map { track -> track?.let { Triple(it.isFavorite, it.albumId, it.favoriteArtistId) } }
            .distinctUntilChanged()
            .flatMapLatest { parts ->
                if (parts == null) return@flatMapLatest flowOf(MediaCenterActions.Favorites.UNKNOWN)
                val (isFavorite, albumId, artistId) = parts
                combine(
                    albumId?.let { albumDao.observeIsFavorite(it) } ?: flowOf(null),
                    artistId?.let { artistDao.observeIsFavorite(it) } ?: flowOf(null),
                ) { album, artist -> MediaCenterActions.Favorites(track = isFavorite, album = album, artist = artist) }
            }

    /**
     * Favorites or unfavorites the current track, its album or its artist: in Room right away, so the button and the
     * Favorites tab follow, then on the server. If the server can't be reached the change is undone, as the next sync
     * would revert it.
     */
    private suspend fun toggleFavorite(target: MediaCenterActions.FavoriteTarget): SessionResult {
        val trackId = currentTrackId.value ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val itemId = when (target) {
            MediaCenterActions.FavoriteTarget.TRACK -> trackId
            MediaCenterActions.FavoriteTarget.ALBUM -> trackDao.getTrackById(trackId)?.albumId
            MediaCenterActions.FavoriteTarget.ARTIST -> trackDao.getTrackById(trackId)?.favoriteArtistId
        } ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val wasFavorite = isFavoriteInRoom(target, itemId) ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        setFavoriteInRoom(target, itemId, !wasFavorite)
        notifyFavoritesChanged()
        val result = userRepository.setFavorite(itemId, !wasFavorite)
        if (result is HarlowResult.Error) {
            Log.w(TAG, "Couldn't update the favorite on the server", result.exception)
            setFavoriteInRoom(target, itemId, wasFavorite)
            notifyFavoritesChanged()
            withContext(Dispatchers.Main) { showNotice(SessionError.ERROR_IO, R.string.favorite_error_offline) }
            return SessionResult(SessionError.ERROR_IO)
        }
        // Only the playing track's user data gets written back by the progress reports, see resyncFavorite.
        if (target == MediaCenterActions.FavoriteTarget.TRACK) favoritesChangedWhilePlaying += trackId
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private suspend fun isFavoriteInRoom(target: MediaCenterActions.FavoriteTarget, itemId: String): Boolean? =
        when (target) {
            MediaCenterActions.FavoriteTarget.TRACK -> trackDao.observeIsFavorite(itemId)
            MediaCenterActions.FavoriteTarget.ALBUM -> albumDao.observeIsFavorite(itemId)
            MediaCenterActions.FavoriteTarget.ARTIST -> artistDao.observeIsFavorite(itemId)
        }.first()

    private suspend fun setFavoriteInRoom(
        target: MediaCenterActions.FavoriteTarget,
        itemId: String,
        isFavorite: Boolean,
    ) = when (target) {
        MediaCenterActions.FavoriteTarget.TRACK -> trackDao.setFavorite(itemId, isFavorite)
        MediaCenterActions.FavoriteTarget.ALBUM -> albumDao.setFavorite(itemId, isFavorite)
        MediaCenterActions.FavoriteTarget.ARTIST -> artistDao.setFavorite(itemId, isFavorite)
    }

    /**
     * Sends a track's favorite to the server again once it stopped playing there ([PlaybackReporting] calls this).
     * Jellyfin (seen on 10.11) writes the user data it had at playback start back with every progress report, which
     * undoes a favorite changed meanwhile; once the track left the session the favorite sticks.
     */
    private suspend fun resyncFavorite(trackId: String) {
        if (!favoritesChangedWhilePlaying.remove(trackId)) return
        val isFavorite = trackDao.observeIsFavorite(trackId).first() ?: return
        val result = userRepository.setFavorite(trackId, isFavorite)
        if (result is HarlowResult.Error) Log.w(TAG, "Couldn't send the favorite to the server again", result.exception)
    }

    private suspend fun notifyFavoritesChanged() = withContext(Dispatchers.Main) {
        val session = mediaLibrarySession ?: return@withContext
        listOf(TAB_HOME, TAB_FAVORITES, FAV_ALBUMS, FAV_ARTISTS, FAV_TRACKS)
            .forEach { session.notifyChildrenChanged(it, Int.MAX_VALUE, null) }
    }

    private fun saveQueue() {
        val exoPlayer = player ?: return
        if (exoPlayer.mediaItemCount == 0) {
            queuePreferences.clear()
            return
        }
        val serverId = activeServerId ?: return
        queuePreferences.saveQueue(
            serverId = serverId,
            trackIds = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId },
            index = exoPlayer.currentMediaItemIndex,
            positionMs = exoPlayer.currentPosition,
            shuffleEnabled = exoPlayer.shuffleModeEnabled,
            repeatMode = exoPlayer.repeatMode,
        )
    }

    private fun savePosition() {
        val exoPlayer = player ?: return
        if (exoPlayer.mediaItemCount == 0) return
        queuePreferences.savePosition(exoPlayer.currentMediaItemIndex, exoPlayer.currentPosition)
    }

    /** A saved queue ready to put back into the player. */
    private class RestorableQueue(
        val serverId: String,
        val items: List<MediaItem>,
        val startIndex: Int,
        val startPositionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
        val wasPlaying: Boolean,
    )

    /**
     * Loads the saved queue, keeping only tracks of its own server that are still in the library. `null` if there is
     * nothing to restore, or the user has since logged out or switched servers.
     */
    private suspend fun loadRestorableQueue(): RestorableQueue? {
        val saved = try {
            queuePreferences.load()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load the saved play queue", e)
            null
        } ?: return null
        if (serverDao.getActiveServer()?.id != saved.serverId) return null

        val tracks = trackDao.getTracksById(saved.trackIds).filterValues { it.serverId == saved.serverId }
        val plan = planQueueRestore(saved.trackIds.size, saved.index, saved.positionMs) {
            saved.trackIds[it] in tracks
        } ?: return null
        val items = enrichMediaItems(
            plan.keptIndices.map { MediaItem.Builder().setMediaId(saved.trackIds[it]).build() },
        )
        // A track removed in the meantime has no stream URI; skip restoring rather than queue an unplayable item.
        if (items.any { it.localConfiguration == null }) return null
        // The user may have logged out or switched servers while this was loading.
        if (serverDao.getActiveServer()?.id != saved.serverId) return null
        return RestorableQueue(
            serverId = saved.serverId,
            items = items,
            startIndex = plan.startIndex,
            startPositionMs = plan.startPositionMs,
            shuffleEnabled = saved.shuffleEnabled,
            repeatMode = saved.repeatMode,
            wasPlaying = saved.wasPlaying,
        )
    }

    /**
     * Puts the saved queue back into an empty [exoPlayer], paused at the saved track and position, so the car's media
     * center can show it. The player isn't prepared: no autoplay on start (MA-1), and nothing is fetched until the user
     * presses play. Only if the user turned on resuming in the settings and the queue was playing when the car was
     * left does it play on.
     *
     * Only done when a media center browses the library (see `onGetLibraryRoot`; it connects as a legacy browser, for
     * which Media3 doesn't call `onPostConnect`). Other callers that start the service, such as System UI
     * probing for its resume card, get the queue through `onPlaybackResumption` when they press play; restoring for
     * them would post a media notification for a player nobody is using.
     */
    private suspend fun restoreQueue(exoPlayer: ExoPlayer) {
        val queue = loadRestorableQueue() ?: return
        val resume = queue.wasPlaying && playbackPreferences.autoResume.first()
        withContext(Dispatchers.Main) {
            // The player may have been released, or given a queue by the app, Android Auto or a playback
            // resumption, meanwhile.
            if (player !== exoPlayer || exoPlayer.mediaItemCount > 0) return@withContext
            activeServerId = queue.serverId
            exoPlayer.shuffleModeEnabled = queue.shuffleEnabled
            exoPlayer.repeatMode = queue.repeatMode
            exoPlayer.setMediaItems(queue.items, queue.startIndex, queue.startPositionMs)
            Log.i(TAG, "Restored a queue of ${queue.items.size} tracks at ${queue.startIndex}, resume=$resume")
            if (resume) {
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            savePosition()
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(savePositionPeriodically)
        savePosition()
        playbackReporting?.release()
        playbackReporting = null
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    /**
     * Keeps only items the player can stream; offline only those in [offlineLibrary] (the rest would stop the queue).
     * If none are left, tells [controller] why and fails: Media3 ignores a failed request, so without the error
     * Android Auto would wait on "Getting your selection…" indefinitely.
     */
    private suspend fun playableOrError(
        controller: MediaSession.ControllerInfo,
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        val offline = if (isOnline()) null else offlineLibrary().trackIds
        val kept = items.map { it.localConfiguration != null && (offline == null || it.mediaId in offline) }
        val playable = items.filterIndexed { index, _ -> kept[index] }
        if (playable.isEmpty()) {
            Log.w(
                TAG,
                "Nothing playable among ${items.size} items for ${controller.packageName}, offline=${offline != null}",
            )
            val message = if (offline != null) {
                R.string.playback_error_offline_not_cached
            } else {
                R.string.playback_error_nothing_playable
            }
            val error = SessionError(SessionError.ERROR_NOT_SUPPORTED, getString(message))
            mediaLibrarySession?.let { session -> handler.post { session.sendError(controller, error) } }
            throw UnsupportedOperationException("Nothing playable")
        }
        if (playable.size < items.size) {
            Log.w(TAG, "Dropped ${items.size - playable.size} items without a stream, offline=${offline != null}")
        }
        val (start, position) = remapStart(kept, startIndex, startPositionMs)
        return MediaSession.MediaItemsWithStartPosition(playable, start, position)
    }

    /** What plays without the server: downloads, and tracks whose stream lies completely in the streaming cache. */
    private class OfflineLibrary(
        /** Cached tracks and the stream of each that is complete in the cache. */
        val streams: Map<String, StreamSource>,
        val trackIds: Set<String>,
        val albumIds: Set<String>,
        val artistNames: Set<String>,
    )

    private val cachedTracks by lazy { CachedTracks(harlowCache.streamingCache) }

    @Volatile private var offlineLibraryMemo: Pair<List<Any?>, OfflineLibrary>? = null

    private suspend fun offlineLibrary(): OfflineLibrary = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer()
        val quality = playbackPreferences.streamQuality.first()
        val downloaded = downloadDao.getDownloadedTrackIds().toSet()
        // The cache only changes by what is played or evicted, and either changes its size.
        val key = listOf(server?.id, quality, downloaded, harlowCache.streamingCache.cacheSpace)
        offlineLibraryMemo?.takeIf { it.first == key }?.let { return@withContext it.second }
        val candidates = if (server == null) {
            emptyMap()
        } else {
            trackDao.getTracksById(cachedTracks.candidateTrackIds() - downloaded)
        }
        val streams = candidates.values.mapNotNull { track ->
            completeStream(server!!.url, track, quality)?.let { track.id to it }
        }.toMap()
        val cached = candidates.filterKeys { it in streams }.values
        OfflineLibrary(
            streams = streams,
            trackIds = downloaded + streams.keys,
            albumIds = downloadDao.getDownloadedAlbumIds().toSet() + cached.mapNotNull { it.albumId },
            artistNames = downloadDao.getDownloadedArtistNames().toSet() + cached.mapNotNull { it.artistName },
        ).also { offlineLibraryMemo = key to it }
    }

    /** The stream of [track] that lies completely in the cache: at the current quality, or the original file. */
    private fun completeStream(serverUrl: String, track: TrackEntity, quality: StreamQuality): StreamSource? =
        listOf(
            streamSource(serverUrl, track.id, quality.transcodeBitrate(track.bitrate)),
            streamSource(serverUrl, track.id, transcodeBitrate = null),
        ).distinct().firstOrNull { cachedTracks.isComplete(it) }

    /**
     * Whether the server is reachable, so the whole library can be offered (otherwise only [offlineLibrary]). Right
     * after the process started nothing has been checked yet and the state reads Offline; the first call waits briefly
     * for the first check, so a voice request or the first browse after starting the car don't end up offline-only.
     */
    private suspend fun isOnline(): Boolean {
        if (!firstConnectionCheck.isCompleted) {
            withTimeoutOrNull(FIRST_CONNECTION_CHECK_TIMEOUT_MS) {
                networkStateObserver.connectionState.first { it != ConnectionState.Offline }
            }
            firstConnectionCheck.complete(Unit)
        }
        return networkStateObserver.connectionState.value == ConnectionState.Connected
    }

    /**
     * The artist that albums and tracks are resolved to. Aliases (the same artist under another id) have no albums or
     * tracks of their own; search lists only canonical artists, but older links and voice requests may name an alias.
     */
    private suspend fun canonicalArtistId(artistId: String): String {
        val serverId = serverDao.getActiveServer()?.id ?: return artistId
        return artistAliasDao.getCanonicalId(serverId, artistId) ?: artistId
    }

    /** Fails a playback request because nobody is signed in, telling [controller] why. */
    private fun failSignedOut(controller: MediaSession.ControllerInfo): Nothing {
        val error = SessionError(SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED, getString(R.string.sign_in_required))
        mediaLibrarySession?.let { session -> handler.post { session.sendError(controller, error) } }
        throw IllegalStateException("Not signed in")
    }

    private fun <T> asyncFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                Log.e(TAG, "asyncFuture failed", e)
                future.setException(e)
            }
        }
        return future
    }

    private val artworkAuthority get() = "$packageName.artwork"

    /**
     * A track shows its album's cover (most tracks have none of their own), in the version of the album's image tag;
     * a track without album its own image.
     */
    private fun TrackEntity.trackArtworkUri(): Uri =
        albumId?.let { ArtworkUris.item(artworkAuthority, it, ArtworkPlaceholder.MUSIC, albumImageTags.value[it]) }
            ?: ArtworkUris.item(artworkAuthority, id, ArtworkPlaceholder.MUSIC, imageTag)

    /** Artwork of an item that may have none ([imageTag] null): then straight the placeholder of its kind. */
    private fun artworkUri(itemId: String, imageTag: String?, placeholder: ArtworkPlaceholder): Uri =
        ArtworkUris.forItem(artworkAuthority, itemId, imageTag, placeholder)

    /**
     * An icon as `android.resource://package/drawable/name`: the car's media center resolves resources by type and
     * name, and crashes on the numeric-id form.
     */
    private fun drawableUri(resId: Int): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(packageName)
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()

    /**
     * The icon of a folder whose layout follows the list-view setting. The media center keeps a folder it already
     * knows when only its layout hint changes, so in the list view the folder gets the icon under another name
     * (`drawable_aliases.xml`): a changed artwork URI makes it take the new folder.
     */
    private fun collectionIcon(@DrawableRes icon: Int): Uri =
        drawableUri(if (collectionStyle == CONTENT_STYLE_LIST_ITEM) LIST_ICON_ALIASES.getValue(icon) else icon)

    /** [collectionIcon] for artwork: the provider ignores the extra query parameter. */
    private fun Uri.forCollectionStyle(): Uri =
        if (collectionStyle == CONTENT_STYLE_LIST_ITEM) {
            buildUpon().appendQueryParameter("layout", "list").build()
        } else {
            this
        }

    private fun AlbumEntity.toBrowsableItem(groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
        }
        val artUri = artworkUri(id, imageTag, ArtworkPlaceholder.ALBUM)
        return MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setSubtitle(
                        artistName?.let { getString(R.string.subtitle_album_by, it) }
                            ?: getString(R.string.subtitle_album),
                    )
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .apply { year?.let { setReleaseYear(it) } }
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun ArtistEntity.toBrowsableItem(groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_BROWSABLE_HINT, collectionStyle)
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
        }
        return MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(getString(R.string.subtitle_artist))
                    .setArtworkUri(artworkUri(id, imageTag, ArtworkPlaceholder.ARTIST).forCollectionStyle())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun PlaylistEntity.toBrowsableItem(): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
        }
        val subtitle = if (trackCount > 0) {
            resources.getQuantityString(R.plurals.subtitle_playlist_tracks, trackCount, trackCount)
        } else {
            getString(R.string.subtitle_playlist)
        }
        val artUri = artworkUri(id, imageTag, ArtworkPlaceholder.PLAYLIST)
        return MediaItem.Builder()
            .setMediaId("playlist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun TrackEntity.toPlayableItem(
        groupTitle: String? = null,
        parentId: String? = null,
    ): MediaItem {
        val extras = Bundle().apply {
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
            putString(EXTRA_PARENT_ID, parentId ?: albumId?.let { "album:$it" } ?: "")
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(trackArtworkUri())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { trackNumber?.let { setTrackNumber(it) } }
                    .apply { discNumber?.let { setDiscNumber(it) } }
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun AlbumEntity.toPlayablePreview(): MediaItem {
        val artUri = artworkUri(id, imageTag, ArtworkPlaceholder.ALBUM)
        return MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(
                        artistName?.let { getString(R.string.subtitle_album_by, it) }
                            ?: getString(R.string.subtitle_album),
                    )
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun ArtistEntity.toPlayablePreview(): MediaItem =
        MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(getString(R.string.subtitle_artist))
                    .setArtworkUri(artworkUri(id, imageTag, ArtworkPlaceholder.ARTIST))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    /** A changed quality applies from the next track on: the current one plays out, the others get new URIs. */
    private suspend fun applyStreamQuality() {
        val exoPlayer = player ?: return
        val trackIds = withContext(Dispatchers.Main) {
            (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId }
        }
        val items = enrichMediaItems(trackIds.map { MediaItem.Builder().setMediaId(it).build() })
        withContext(Dispatchers.Main) {
            // The queue may have changed meanwhile; it then got the new quality anyway.
            val unchanged = trackIds == (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId }
            if (player !== exoPlayer || !unchanged) return@withContext
            val current = exoPlayer.currentMediaItemIndex
            val playable = items.mapIndexed { index, item ->
                if (item.localConfiguration != null) item else exoPlayer.getMediaItemAt(index)
            }
            exoPlayer.replaceMediaItems(current + 1, playable.size, playable.drop(current + 1))
            exoPlayer.replaceMediaItems(0, current, playable.take(current))
        }
    }

    /** The server can't or may not transcode this track: it plays on as the original rather than not at all. */
    private fun fallBackToOriginal(exoPlayer: ExoPlayer, error: PlaybackException) {
        val item = exoPlayer.currentMediaItem ?: return
        if (item.localConfiguration?.mimeType != MimeTypes.APPLICATION_M3U8) return
        if (!shouldFallBackToOriginal(error.errorCode, error.httpStatusCode())) return
        val index = exoPlayer.currentMediaItemIndex
        val positionMs = exoPlayer.currentPosition
        serviceScope.launch {
            val serverUrl = serverDao.getActiveServer()?.url ?: return@launch
            withContext(Dispatchers.Main) {
                if (player !== exoPlayer || exoPlayer.currentMediaItem?.mediaId != item.mediaId) return@withContext
                Log.w(TAG, "Transcoding failed, playing the original")
                val original = item.buildUpon()
                    .setUri(jellyfinStreamUrl(serverUrl, item.mediaId))
                    .setMimeType(null)
                    .setCustomCacheKey(item.mediaId)
                    .build()
                exoPlayer.replaceMediaItem(index, original)
                exoPlayer.seekTo(index, positionMs)
                exoPlayer.prepare()
            }
        }
    }

    private suspend fun enrichMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val server = serverDao.getActiveServer()
        if (server == null) {
            Log.w(TAG, "enrichMediaItems: no active server, returning ${mediaItems.size} unenriched items")
            return mediaItems
        }
        val serverUrl = server.url
        val quality = playbackPreferences.streamQuality.first()
        // Downloads are kept under the track's ID and play from there, whatever the quality.
        val downloaded = downloadDao.getDownloadedTrackIds().toSet()
        // Offline, a track plays from the stream that lies complete in the cache. Doesn't wait for the first server
        // check (that only affects which complete stream is picked, not whether one is).
        val offlineStreams = if (networkStateObserver.connectionState.value == ConnectionState.Connected) {
            emptyMap()
        } else {
            offlineLibrary().streams
        }

        var enrichedCount = 0
        var missCount = 0
        val tracksById = trackDao.getTracksById(
            mediaItems.filter { it.localConfiguration == null }.map { it.mediaId },
        )
        val result = mediaItems.map { item ->
            val trackId = item.mediaId
            if (item.localConfiguration != null) {
                enrichedCount++
                return@map item
            }

            val track = tracksById[trackId]
            if (track != null) {
                enrichedCount++
                MediaItem.Builder()
                    .setMediaId(trackId)
                    .apply {
                        // Segments are cached by their URL, which includes the bitrate.
                        val source = offlineStreams[trackId] ?: streamSource(
                            serverUrl,
                            trackId,
                            quality.transcodeBitrate(track.bitrate),
                            original = trackId in downloaded,
                        )
                        setUri(source.uri)
                        if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                        source.customCacheKey?.let { setCustomCacheKey(it) }
                    }
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.name)
                            .setArtist(track.artistName)
                            .setAlbumTitle(track.albumName)
                            .setArtworkUri(track.trackArtworkUri())
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .build()
                    )
                    .build()
            } else {
                missCount++
                Log.w(TAG, "enrichMediaItems: track not found in Room: $trackId")
                item
            }
        }
        Log.d(TAG, "enrichMediaItems: ${result.size} items, $enrichedCount enriched, $missCount not found")
        return result
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        /** Every controller, the media center and the platform session included, may use the custom actions. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .apply { MediaCenterActions.commands.forEach { add(it) } }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val exoPlayer = player
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
            return when (customCommand.customAction) {
                MediaCenterActions.ACTION_TOGGLE_SHUFFLE -> {
                    exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                MediaCenterActions.ACTION_CYCLE_REPEAT -> {
                    exoPlayer.repeatMode = MediaCenterActions.nextRepeatMode(exoPlayer.repeatMode)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                MediaCenterActions.ACTION_TOGGLE_FAVORITE ->
                    asyncFuture { toggleFavorite(MediaCenterActions.FavoriteTarget.TRACK) }
                MediaCenterActions.ACTION_TOGGLE_ALBUM_FAVORITE ->
                    asyncFuture { toggleFavorite(MediaCenterActions.FavoriteTarget.ALBUM) }
                MediaCenterActions.ACTION_TOGGLE_ARTIST_FAVORITE ->
                    asyncFuture { toggleFavorite(MediaCenterActions.FavoriteTarget.ARTIST) }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onSetMediaItems(
            session: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.d(TAG, "onSetMediaItems: ${mediaItems.size} items, startIndex=$startIndex, " +
                "ids=${mediaItems.map { it.mediaId }}, " +
                "controller=${browser.packageName}")
            return asyncFuture {
                if (serverDao.getActiveServer() == null) {
                    return@asyncFuture failSignedOut(browser)
                }
                val searchQuery = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
                if (searchQuery != null) {
                    val extras = mediaItems[0].requestMetadata.extras
                    return@asyncFuture playFromSearch(browser, searchQuery, extras)
                }
                if (mediaItems.size == 1) {
                    val mediaId = mediaItems[0].mediaId
                    Log.d(TAG, "onSetMediaItems: resolving single item mediaId=$mediaId")

                    if (mediaId.startsWith("album:")) {
                        val albumId = mediaId.removePrefix("album:")
                        val tracks = trackDao.getTracksByAlbumSync(albumId)
                        Log.d(TAG, "onSetMediaItems: album:$albumId → ${tracks.size} tracks")
                        if (tracks.isNotEmpty()) {
                            val enriched = enrichMediaItems(tracks.map {
                                it.toPlayableItem(parentId = "album:$albumId")
                            })
                            Log.d(TAG, "onSetMediaItems: resolved album, returning ${enriched.size} items")
                            return@asyncFuture playableOrError(browser, enriched, 0, startPositionMs)
                        }
                    }
                    if (mediaId.startsWith("artist:")) {
                        // Same as the app's artist Play All: the artist's top tracks by resolved artist, which also
                        // covers artists that only appear on other artists' albums.
                        val artistId = canonicalArtistId(mediaId.removePrefix("artist:"))
                        val tracks = trackDao.getTracksByResolvedArtistSync(artistId)
                        Log.d(TAG, "onSetMediaItems: artist:$artistId → ${tracks.size} tracks")
                        return@asyncFuture playableOrError(
                            browser,
                            enrichMediaItems(tracks.map { it.toPlayableItem(parentId = "artist:$artistId") }),
                            0,
                            startPositionMs,
                        )
                    }

                    if (mediaId.startsWith("playlist:")) {
                        // A whole playlist, e.g. from a deep link (the media center itself plays its tracks).
                        val tracks = playlistDao.getPlaylistTracksSync(mediaId.removePrefix("playlist:"))
                        Log.d(TAG, "onSetMediaItems: $mediaId → ${tracks.size} tracks")
                        return@asyncFuture playableOrError(
                            browser,
                            enrichMediaItems(tracks.map { it.toPlayableItem(parentId = mediaId) }),
                            0,
                            startPositionMs,
                        )
                    }

                    val trackId = mediaId
                    val parentId = mediaItems[0].mediaMetadata.extras?.getString(EXTRA_PARENT_ID)
                    val track = trackDao.getTrackById(trackId)
                    val serverId = serverDao.getActiveServer()?.id ?: ""
                    Log.d(TAG, "onSetMediaItems: track lookup id=$trackId, found=${track != null}, parentId=$parentId, serverId=$serverId")

                    val siblings = when {
                        parentId?.startsWith("playlist:") == true -> {
                            val plId = parentId.removePrefix("playlist:")
                            playlistDao.getPlaylistTracksSync(plId)
                        }
                        parentId == FAV_TRACKS -> {
                            trackDao.getFavoriteTracksSync(serverId)
                        }
                        parentId == LIBRARY_SONGS -> {
                            trackDao.getAllTracksByServer(serverId)
                        }
                        parentId?.startsWith("album:") == true -> {
                            val aId = parentId.removePrefix("album:")
                            trackDao.getTracksByAlbumSync(aId)
                        }
                        track?.albumId != null -> {
                            trackDao.getTracksByAlbumSync(track.albumId!!)
                        }
                        else -> null
                    }
                    Log.d(TAG, "onSetMediaItems: siblings=${siblings?.size}, source=${
                        when {
                            parentId?.startsWith("playlist:") == true -> "playlist"
                            parentId == FAV_TRACKS -> "fav_tracks"
                            parentId == LIBRARY_SONGS -> "library_songs"
                            parentId?.startsWith("album:") == true -> "album_parent"
                            track?.albumId != null -> "album_fallback"
                            else -> "none"
                        }
                    }")

                    if (!siblings.isNullOrEmpty()) {
                        val enriched = enrichMediaItems(siblings.map {
                            it.toPlayableItem(parentId = parentId)
                        })
                        val idx = siblings.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        Log.d(TAG, "onSetMediaItems: returning ${enriched.size} sibling items, startIdx=$idx")
                        playableOrError(browser, enriched, idx, startPositionMs)
                    } else {
                        val enriched = enrichMediaItems(mediaItems)
                        Log.d(TAG, "onSetMediaItems: no siblings, returning ${enriched.size} single items")
                        playableOrError(browser, enriched, startIndex, startPositionMs)
                    }
                } else {
                    Log.d(TAG, "onSetMediaItems: multi-item (${mediaItems.size}), enriching directly")
                    playableOrError(browser, enrichMediaItems(mediaItems), startIndex, startPositionMs)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            Log.d(TAG, "onAddMediaItems: ${mediaItems.size} items, ids=${mediaItems.map { it.mediaId }}")
            return asyncFuture {
                val searchQuery = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
                if (searchQuery != null) {
                    return@asyncFuture playFromSearch(controller, searchQuery, mediaItems[0].requestMetadata.extras)
                        .mediaItems
                }
                // Items that couldn't be resolved to a stream would make the player fail; leave them out.
                enrichMediaItems(mediaItems).filter { it.localConfiguration != null }
            }
        }

        /**
         * A voice request ("play X by Y") from the assistant or the OEM's voice control, which Media3 hands over as a
         * media item with a search query instead of the old MEDIA_PLAY_FROM_SEARCH intent.
         */
        private suspend fun playFromSearch(
            controller: MediaSession.ControllerInfo,
            query: String,
            extras: Bundle?,
        ): MediaSession.MediaItemsWithStartPosition {
            val serverId = serverDao.getActiveServer()?.id ?: return failSignedOut(controller)
            val request = playSearchRequest(query) { key -> extras?.getString(key) }
            val available = if (isOnline()) null else offlineLibrary().trackIds
            val playback = playFromSearchResolver.resolve(serverId, request) { available == null || it.id in available }
            Log.i(TAG, "playFromSearch: focus=${request.focus} tracks=${playback?.tracks?.size ?: 0}")
            val items = playback?.tracks.orEmpty().map { it.toPlayableItem() }
            return playableOrError(controller, enrichMediaItems(items), playback?.startIndex ?: 0, C.TIME_UNSET)
        }

        /**
         * Play was requested while the player has no current item: from a headset or Bluetooth button (via
         * MediaButtonReceiver) after the app was gone, from the system's media controls after a reboot, or from a
         * controller such as Android Auto. Media3 sets the returned queue and starts playback; System UI also uses it
         * to show the last-played track on its resumption card.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val queue = try {
                    loadRestorableQueue()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load the queue to resume", e)
                    null
                }
                // Complete on the main thread, where Media3 applies the result in the same turn. If the player got
                // a queue meanwhile (the app restored it, or Android Auto picked something), answer with that queue
                // instead of overwriting it with the saved one.
                withContext(Dispatchers.Main) {
                    val exoPlayer = player
                    when {
                        exoPlayer == null -> future.setException(IllegalStateException("The player was released"))
                        exoPlayer.mediaItemCount > 0 -> future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it) },
                                exoPlayer.currentMediaItemIndex,
                                exoPlayer.currentPosition,
                            ),
                        )
                        queue == null -> future.setException(UnsupportedOperationException("No play queue to resume"))
                        else -> {
                            activeServerId = queue.serverId
                            exoPlayer.shuffleModeEnabled = queue.shuffleEnabled
                            exoPlayer.repeatMode = queue.repeatMode
                            Log.i(
                                TAG,
                                "Resuming a queue of ${queue.items.size} tracks at ${queue.startIndex} " +
                                    "for ${controller.packageName}",
                            )
                            future.set(
                                MediaSession.MediaItemsWithStartPosition(
                                    queue.items, queue.startIndex, queue.startPositionMs,
                                ),
                            )
                        }
                    }
                }
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (browser.packageName in MEDIA_CENTER_PACKAGES && params?.isRecent != true) {
                player?.let { exoPlayer -> serviceScope.launch { restoreQueue(exoPlayer) } }
            }
            val rootExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, collectionStyle)
            }
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Harlow")
                        .setExtras(rootExtras)
                        .build()
                )
                .build()
            val rootParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
        }

        /**
         * Accepts every subscription. Media3's default looks the parent up with `onGetItem`, which doesn't know every
         * node (e.g. the favorites' folders), and so rejects some of them; `notifyChildrenChanged` then never reaches
         * the media center, which keeps showing a stale (e.g. offline or signed-out) list.
         */
        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            if (browser.controllerVersion != MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION) {
                // Legacy browsers (the media center) get the children through onLoadChildren anyway.
                session.notifyChildrenChanged(browser, parentId, Int.MAX_VALUE, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        /**
         * One node of the content tree, for a media center opened at it (a deep link with `EXTRA_KEY_MEDIA_ID`, see
         * [DeepLink.Browse]) to show its title.
         */
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = asyncFuture {
            serverDao.getActiveServer() ?: return@asyncFuture signInRequired()
            refreshCollectionStyle()
            val item = when {
                mediaId == ROOT_ID -> null
                mediaId in ROOT_TABS -> rootChildren().first { it.mediaId == mediaId }
                mediaId.startsWith("album:") -> albumDao.getAlbumById(mediaId.removePrefix("album:"))
                    ?.toBrowsableItem()
                mediaId.startsWith("artist:") ->
                    artistDao.getArtistById(canonicalArtistId(mediaId.removePrefix("artist:")))?.toBrowsableItem()
                mediaId.startsWith("playlist:") -> playlistDao.getPlaylistById(mediaId.removePrefix("playlist:"))
                    ?.toBrowsableItem()
                mediaId.startsWith("genre:") -> browsableItem(
                    mediaId = mediaId,
                    title = mediaId.removePrefix("genre:"),
                    mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
                    browsableHint = collectionStyle,
                    iconUri = collectionIcon(R.drawable.ic_aa_genres),
                )
                else -> trackDao.getTrackById(mediaId)?.toPlayableItem()
            }
            if (item != null) {
                LibraryResult.ofItem(item, null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            browsedParents += parentId
            return asyncFuture {
                val server = serverDao.getActiveServer() ?: return@asyncFuture signInRequired()
                val serverId = server.id
                refreshCollectionStyle()
                val staticChildren = when (parentId) {
                    ROOT_ID -> rootChildren()
                    TAB_LIBRARY -> libraryChildren()
                    else -> null
                }
                if (staticChildren != null) {
                    return@asyncFuture LibraryResult.ofItemList(ImmutableList.copyOf(staticChildren), params)
                }
                val isOnline = isOnline()
                val offline = if (!isOnline) offlineLibrary() else null
                val dlTrackIds = offline?.trackIds
                val dlAlbumIds = offline?.albumIds
                val dlArtistNames = offline?.artistNames

                fun List<TrackEntity>.onlineFilter() =
                    if (dlTrackIds != null) filter { it.id in dlTrackIds } else this
                fun List<AlbumEntity>.onlineFilter() =
                    if (dlAlbumIds != null) filter { it.id in dlAlbumIds } else this
                fun List<ArtistEntity>.onlineFilter() =
                    if (dlArtistNames != null) filter { it.name in dlArtistNames } else this

                val items = when {
                    parentId == TAB_HOME -> {
                        val recentAlbums = albumDao.getRecentlyPlayedAlbumsSync(serverId, limit = HOME_ROW_SIZE)
                            .onlineFilter()
                        val recentItems = recentAlbums
                            .map { it.toBrowsableItem(groupTitle = getString(R.string.group_recently_played)) }

                        val addedAlbums = albumDao.getRecentlyAddedAlbums(serverId, limit = HOME_ROW_SIZE)
                            .onlineFilter()
                        val addedItems = addedAlbums
                            .map { it.toBrowsableItem(groupTitle = getString(R.string.group_recently_added)) }

                        val usedIds = recentAlbums.map { it.id }.toSet() +
                            addedAlbums.map { it.id }.toSet()

                        val mostPlayed = albumDao.getMostPlayedAlbumsSync(serverId, limit = 20)
                            .onlineFilter()
                        val favoriteAlbums = albumDao.getFavoriteAlbumsSync(serverId)
                            .onlineFilter()
                        val quickPicks = (mostPlayed + favoriteAlbums)
                            .distinctBy { it.id }
                            .filter { it.id !in usedIds }
                            .shuffled()
                            .take(HOME_ROW_SIZE)
                            .map { it.toBrowsableItem(groupTitle = getString(R.string.group_quick_picks)) }

                        val favoriteTracks = trackDao.getFavoriteTracksSync(serverId)
                            .onlineFilter()
                            .shuffled()
                            .take(HOME_ROW_SIZE)
                            .map { it.toPlayableItem(groupTitle = getString(R.string.group_favorite_tracks)) }

                        recentItems + addedItems + quickPicks + favoriteTracks
                    }
                    parentId == TAB_FAVORITES -> {
                        val items = mutableListOf<MediaItem>()

                        val favAlbums = albumDao.getFavoriteAlbumsSync(serverId).onlineFilter()
                        if (favAlbums.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ALBUMS, title = getString(R.string.folder_albums),
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                                browsableHint = collectionStyle,
                                iconUri = collectionIcon(R.drawable.ic_aa_albums),
                            ))
                            items.addAll(favAlbums.take(HOME_ROW_SIZE)
                                .map { it.toPlayablePreview() })
                        }

                        val favArtists = artistDao.getFavoriteArtistsSync(serverId).onlineFilter()
                        if (favArtists.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ARTISTS, title = getString(R.string.folder_artists),
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                                browsableHint = collectionStyle,
                                iconUri = collectionIcon(R.drawable.ic_aa_artists),
                            ))
                            items.addAll(favArtists.take(HOME_ROW_SIZE)
                                .map { it.toPlayablePreview() })
                        }

                        val favTracks = trackDao.getFavoriteTracksSync(serverId).onlineFilter()
                        if (favTracks.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_TRACKS, title = getString(R.string.folder_tracks),
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                                playableHint = collectionStyle,
                                iconUri = collectionIcon(R.drawable.ic_aa_songs),
                            ))
                            items.addAll(favTracks.take(HOME_ROW_SIZE)
                                .map { it.toPlayableItem(parentId = FAV_TRACKS) })
                        }

                        items
                    }
                    parentId == FAV_ALBUMS -> {
                        albumDao.getFavoriteAlbumsSync(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_ARTISTS -> {
                        artistDao.getFavoriteArtistsSync(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_TRACKS -> {
                        trackDao.getFavoriteTracksSync(serverId)
                            .onlineFilter()
                            .map { it.toPlayableItem(parentId = FAV_TRACKS) }
                    }
                    parentId == LIBRARY_ALBUMS -> {
                        albumDao.getAllAlbumsByServer(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_ARTISTS -> {
                        artistDao.getCanonicalArtistsByServer(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_GENRES -> {
                        val genreAlbums = albumDao.getRawGenreStrings(serverId)
                        genreAlbums
                            .flatMap { it.split(GENRE_SEPARATOR) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                            .map { genre ->
                                browsableItem(
                                    mediaId = "genre:$genre",
                                    title = genre,
                                    mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
                                    browsableHint = collectionStyle,
                                    iconUri = collectionIcon(R.drawable.ic_aa_genres),
                                )
                            }
                    }
                    parentId == LIBRARY_SONGS -> {
                        val allTracks = trackDao.getTracksByServerPaged(serverId, limit = AA_MAX_ITEMS, offset = 0)
                        val filtered = allTracks.onlineFilter()
                        Log.d(TAG, "LIBRARY_SONGS: serverId=$serverId, isOnline=$isOnline, " +
                            "total=${allTracks.size}, afterFilter=${filtered.size}, " +
                            "dlTrackIds=${dlTrackIds?.size}")
                        filtered.map { it.toPlayableItem() }
                    }
                    parentId == TAB_PLAYLISTS -> {
                        playlistDao.getPlaylistsByServer(serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("album:") -> {
                        val albumId = parentId.removePrefix("album:")
                        trackDao.getTracksByAlbumSync(albumId)
                            .onlineFilter()
                            .map { it.toPlayableItem() }
                    }
                    parentId.startsWith("artist:") -> {
                        // Same artist as the app's artist screen: resolved, so duplicates merge and artists that
                        // only appear on other artists' albums still have their tracks.
                        val artistId = canonicalArtistId(parentId.removePrefix("artist:"))
                        val albums = albumDao.getAllAlbumsByResolvedArtist(artistId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                        albums.ifEmpty {
                            trackDao.getTracksByResolvedArtistSync(artistId)
                                .onlineFilter()
                                .map { it.toPlayableItem(parentId = parentId) }
                        }
                    }
                    parentId.startsWith("genre:") -> {
                        val genre = parentId.removePrefix("genre:")
                        albumDao.getAlbumsByGenre(genre, serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("playlist:") -> {
                        val playlistId = parentId.removePrefix("playlist:")
                        playlistDao.getPlaylistTracksSync(playlistId)
                            .onlineFilter()
                            .map { it.toPlayableItem(parentId = parentId) }
                    }
                    else -> emptyList()
                }
                // Info level so it survives release builds: Android Auto sometimes shows "No items" right after
                // connecting, and this records whether the connection state made us filter to downloads only.
                Log.i(
                    TAG,
                    "onGetChildren: parent=$parentId state=${networkStateObserver.connectionState.value} " +
                        "offlineOnly=${!isOnline} items=${items.size} controller=${browser.packageName}",
                )
                // Offline with nothing cached for this list: say why instead of an empty page (once the player
                // reported an error, the media center shows nothing at all for an empty list).
                val shown = if (!isOnline && items.isEmpty()) listOf(offlineHintItem()) else items
                LibraryResult.ofItemList(ImmutableList.copyOf(shown), params)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""
                val isOnline = isOnline()
                val offline = if (!isOnline) offlineLibrary() else null
                val dlTrackIds = offline?.trackIds
                val dlAlbumIds = offline?.albumIds
                val dlArtistNames = offline?.artistNames

                val artists = artistDao.searchCanonical(serverId, query, limit = 5)
                    .let { if (dlArtistNames != null) it.filter { a -> a.name in dlArtistNames } else it }
                val albums = albumDao.search(serverId, query, limit = 10)
                    .let { if (dlAlbumIds != null) it.filter { a -> a.id in dlAlbumIds } else it }
                val tracks = trackDao.search(serverId, query, limit = 20)
                    .let { if (dlTrackIds != null) it.filter { t -> t.id in dlTrackIds } else it }
                session.notifySearchResultChanged(
                    browser, query, artists.size + albums.size + tracks.size, params,
                )
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (query.isBlank()) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            return asyncFuture {
                val server = serverDao.getActiveServer() ?: return@asyncFuture signInRequired()
                val serverId = server.id
                refreshCollectionStyle()
                val isOnline = isOnline()
                val offline = if (!isOnline) offlineLibrary() else null
                val dlTrackIds = offline?.trackIds
                val dlAlbumIds = offline?.albumIds
                val dlArtistNames = offline?.artistNames

                val artists = artistDao.searchCanonical(serverId, query, limit = 5)
                    .let { if (dlArtistNames != null) it.filter { a -> a.name in dlArtistNames } else it }
                    .map { it.toBrowsableItem() }
                val albums = albumDao.search(serverId, query, limit = 10)
                    .let { if (dlAlbumIds != null) it.filter { a -> a.id in dlAlbumIds } else it }
                    .map { it.toBrowsableItem() }
                val tracks = trackDao.search(serverId, query, limit = 20)
                    .let { if (dlTrackIds != null) it.filter { t -> t.id in dlTrackIds } else it }
                    .map { it.toPlayableItem() }

                LibraryResult.ofItemList(ImmutableList.copyOf(artists + albums + tracks), params)
            }
        }

        /** The media center's tabs: at most four, each with a white icon (see [ROOT_TABS]). */
        private fun rootChildren(): List<MediaItem> = listOf(
            browsableItem(
                mediaId = TAB_HOME,
                title = getString(R.string.tab_home),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_GRID_ITEM,
                playableHint = CONTENT_STYLE_GRID_ITEM,
                iconUri = drawableUri(R.drawable.ic_tab_home),
            ),
            browsableItem(
                mediaId = TAB_LIBRARY,
                title = getString(R.string.tab_library),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
                iconUri = drawableUri(R.drawable.ic_tab_library),
            ),
            browsableItem(
                mediaId = TAB_PLAYLISTS,
                title = getString(R.string.tab_playlists),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                browsableHint = collectionStyle,
                iconUri = collectionIcon(R.drawable.ic_aa_playlists),
            ),
            browsableItem(
                mediaId = TAB_FAVORITES,
                title = getString(R.string.tab_favorites),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
                playableHint = CONTENT_STYLE_GRID_ITEM,
                iconUri = drawableUri(R.drawable.ic_tab_favorites),
            ),
        )

        private fun libraryChildren(): List<MediaItem> = listOf(
            browsableItem(
                mediaId = LIBRARY_ALBUMS,
                title = getString(R.string.folder_albums),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                browsableHint = collectionStyle,
                iconUri = collectionIcon(R.drawable.ic_aa_albums),
            ),
            browsableItem(
                mediaId = LIBRARY_ARTISTS,
                title = getString(R.string.folder_artists),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                browsableHint = collectionStyle,
                iconUri = collectionIcon(R.drawable.ic_aa_artists),
            ),
            browsableItem(
                mediaId = LIBRARY_GENRES,
                title = getString(R.string.folder_genres),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
                iconUri = drawableUri(R.drawable.ic_aa_genres),
            ),
            browsableItem(
                mediaId = LIBRARY_SONGS,
                title = getString(R.string.folder_songs),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                playableHint = collectionStyle,
                iconUri = collectionIcon(R.drawable.ic_aa_songs),
            ),
        )

        /**
         * Tells why an offline list is empty. Playable, as the media center leaves out items that are neither browsable
         * nor playable; playing it fails with the same explanation (nothing of it is in the offline library).
         */
        private fun offlineHintItem(): MediaItem = MediaItem.Builder()
            .setMediaId(OFFLINE_HINT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.offline_hint_title))
                    .setSubtitle(getString(R.string.offline_hint_subtitle))
                    .setArtworkUri(ArtworkUris.placeholder(artworkAuthority, ArtworkPlaceholder.MUSIC))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

        private fun browsableItem(
            mediaId: String,
            title: String,
            mediaType: Int? = null,
            browsableHint: Int? = null,
            playableHint: Int? = null,
            groupTitle: String? = null,
            iconUri: Uri? = null,
        ): MediaItem {
            val extras = if (browsableHint != null || playableHint != null || groupTitle != null) {
                Bundle().apply {
                    browsableHint?.let { putInt(CONTENT_STYLE_BROWSABLE_HINT, it) }
                    playableHint?.let { putInt(CONTENT_STYLE_PLAYABLE_HINT, it) }
                    groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
                }
            } else null
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .apply { iconUri?.let { setArtworkUri(it) } }
                        .apply { mediaType?.let { setMediaType(it) } }
                        .apply { extras?.let { setExtras(it) } }
                        .build()
                )
                .build()
        }
    }

    companion object {
        private const val TAG = "HarlowMediaService"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L
        private const val FIRST_CONNECTION_CHECK_TIMEOUT_MS = 3_000L
        private const val OFFLINE_HINT_ID = "offline_hint"
        private const val STREAMING_CACHE_SETTLE_MS = 3_000L
        private const val ROOT_ID = "harlow_root"
        internal const val TAB_HOME = "tab_home"
        internal const val TAB_LIBRARY = "tab_library"
        internal const val TAB_PLAYLISTS = "tab_playlists"
        internal const val TAB_FAVORITES = "tab_favorites"
        private const val LIBRARY_ALBUMS = "library_albums"
        private const val LIBRARY_ARTISTS = "library_artists"
        private const val LIBRARY_GENRES = "library_genres"
        private const val LIBRARY_SONGS = "library_songs"
        private const val FAV_ALBUMS = "fav_albums"
        private const val FAV_ARTISTS = "fav_artists"
        private const val FAV_TRACKS = "fav_tracks"
        private val ROOT_TABS = listOf(TAB_HOME, TAB_LIBRARY, TAB_PLAYLISTS, TAB_FAVORITES)

        /** Car media centers that browse the library: AOSP's and those of OEM head units. */
        private val MEDIA_CENTER_PACKAGES = setOf(
            "com.android.car.media",
            "com.harman.media.server",
            "com.harman.ignite.media",
        )
        private const val HOME_ROW_SIZE = 3
        private const val AA_MAX_ITEMS = 500
        private const val EXTRA_PARENT_ID = "dev.harlow.PARENT_ID"
        private const val GENRE_SEPARATOR = "|||"
        private const val CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GROUP_TITLE =
            "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM = 1
        private const val CONTENT_STYLE_GRID_ITEM = 2

        /** The content-style hint for the library's long lists: the list-view setting, or else a cover grid. */
        internal fun collectionStyleOf(listView: Boolean): Int =
            if (listView) CONTENT_STYLE_LIST_ITEM else CONTENT_STYLE_GRID_ITEM

        /** The folder icons and their aliases for the list view, see [collectionIcon]. */
        private val LIST_ICON_ALIASES = mapOf(
            R.drawable.ic_aa_albums to R.drawable.ic_aa_albums_list,
            R.drawable.ic_aa_artists to R.drawable.ic_aa_artists_list,
            R.drawable.ic_aa_genres to R.drawable.ic_aa_genres_list,
            R.drawable.ic_aa_playlists to R.drawable.ic_aa_playlists_list,
            R.drawable.ic_aa_songs to R.drawable.ic_aa_songs_list,
        )
    }
}

private class ContentBitmapLoader(
    private val context: android.content.Context,
) : androidx.media3.session.BitmapLoader {

    private val fallback = SimpleBitmapLoader()
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme == "content") {
            val future = SettableFuture.create<Bitmap>()
            executor.execute {
                try {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        future.setException(java.io.IOException("Failed to decode bitmap from $uri"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
        return fallback.loadBitmap(uri)
    }
}

/** The artist a track's artist favorite goes to: the canonical one of its aliases, as the library lists it. */
private val TrackEntity.favoriteArtistId: String?
    get() = resolvedArtistId ?: artistId
