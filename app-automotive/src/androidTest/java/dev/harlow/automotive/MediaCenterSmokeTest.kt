package dev.harlow.automotive

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Talks to HarlowMediaService the way the car's media center and voice control do: through the platform
 * MediaBrowser/MediaController. Needs a signed-in Jellyfin server with music on the device; skipped otherwise.
 *
 * On AAOS the app runs as the driver, so run it for that user, e.g.:
 * `./gradlew :app-automotive:installDebug :app-automotive:installDebugAndroidTest` and
 * `adb shell am instrument -w --user 10 -e class dev.harlow.automotive.MediaCenterSmokeTest
 *   eu.hwld.harlow.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class MediaCenterSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val main = Handler(Looper.getMainLooper())
    private lateinit var browser: MediaBrowser
    private lateinit var controller: MediaController

    @Before
    fun connect() {
        val connected = CountDownLatch(1)
        main.post {
            browser = MediaBrowser(
                context,
                ComponentName(context, "dev.harlow.core.player.HarlowMediaService"),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() = connected.countDown()
                },
                null,
            )
            browser.connect()
        }
        assertTrue("Couldn't connect to HarlowMediaService", connected.await(TIMEOUT_S, TimeUnit.SECONDS))
        controller = MediaController(context, browser.sessionToken)
    }

    @After
    fun disconnect() {
        if (::controller.isInitialized) controller.transportControls.pause()
        main.post { browser.disconnect() }
    }

    @Test
    fun rootHasAtMostFourTabsWithTitlesAndIcons() {
        val tabs = children(browser.root)
        assumeTrue("Not signed in", tabs != null)
        assertTrue(tabs!!.size in 1..4)
        tabs.forEach { tab ->
            assertTrue(tab.isBrowsable)
            assertTrue(!tab.description.title.isNullOrBlank())
            assertNotNull("Tab ${tab.description.title} has no icon", tab.description.iconUri)
        }
    }

    @Test
    fun noAutoplayAfterConnecting() {
        Thread.sleep(2_000)
        val state = controller.playbackState?.state
        assertTrue("Playing without a request: $state", state != PlaybackState.STATE_PLAYING)
    }

    @Test
    fun voiceRequestForAnArtistPlaysThatArtist() {
        val artist = firstArtistName()
        assumeTrue("No artists in the library (or not signed in)", artist != null)
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_ARTIST, artist)
        }
        controller.transportControls.playFromSearch(artist, extras)

        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
        val playing = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        assertTrue("Playing \"$playing\" for \"$artist\"", playing.contains(artist!!, ignoreCase = true))
    }

    @Test
    fun voiceRequestForAnAlbumPlaysThatAlbum() {
        val album = firstChild(TAB_LIBRARY, "library_albums")?.description?.title?.toString()
        assumeTrue("No albums in the library (or not signed in)", album != null)
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_ALBUM, album)
        }
        controller.transportControls.playFromSearch(album, extras)

        assertPlaying(MediaMetadata.METADATA_KEY_ALBUM, album!!)
    }

    @Test
    fun freeTextVoiceRequestForAnAlbumPlaysThatAlbum() {
        val album = firstChild(TAB_LIBRARY, "library_albums")?.description?.title?.toString()
        assumeTrue("No albums in the library (or not signed in)", album != null)
        controller.transportControls.playFromSearch(album, Bundle())

        assertPlaying(MediaMetadata.METADATA_KEY_ALBUM, album!!)
    }

    @Test
    fun voiceRequestForATrackPlaysThatTrack() {
        val title = firstChild(TAB_LIBRARY, "library_songs")?.description?.title?.toString()
        assumeTrue("No tracks in the library (or not signed in)", title != null)
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_TITLE, title)
        }
        controller.transportControls.playFromSearch(title, extras)

        assertPlaying(MediaMetadata.METADATA_KEY_TITLE, title!!)
    }

    @Test
    fun freeTextVoiceRequestForATrackPlaysThatTrack() {
        val title = firstChild(TAB_LIBRARY, "library_songs")?.description?.title?.toString()
        assumeTrue("No tracks in the library (or not signed in)", title != null)
        controller.transportControls.playFromSearch(title, Bundle())

        // An artist or album of the same name would win over the track; then this check doesn't apply.
        val state = awaitState(PlaybackState.STATE_PLAYING)
        assertEquals(PlaybackState.STATE_PLAYING, state)
        val playing = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val album = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        assertTrue(
            "Playing \"$playing\" for \"$title\"",
            listOf(playing, album, artist).any { it.equals(title, ignoreCase = true) },
        )
    }

    @Test
    fun voiceRequestForAPlaylistPlaysThatPlaylist() {
        val playlist = firstChild(TAB_PLAYLISTS)
        val name = playlist?.description?.title?.toString()
        val firstTrack = playlist?.mediaId?.let { children(it) }?.firstOrNull()?.description?.title?.toString()
        assumeTrue("No playlist with tracks (or not signed in)", name != null && firstTrack != null)
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_PLAYLIST, name)
        }
        controller.transportControls.playFromSearch(name, extras)

        // A playlist starts at its first track.
        assertPlaying(MediaMetadata.METADATA_KEY_TITLE, firstTrack!!)
    }

    @Test
    fun voiceRequestForSomethingUnknownDoesNotPlay() {
        assumeTrue("Not signed in", children(browser.root) != null)
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_ALBUM, UNKNOWN)
        }
        controller.transportControls.playFromSearch(UNKNOWN, extras)

        Thread.sleep(3_000)
        val state = controller.playbackState?.state
        assertTrue("Playing something for an unknown album: $state", state != PlaybackState.STATE_PLAYING)
    }

    @Test
    fun emptyVoiceRequestPlaysSomething() {
        assumeTrue("Not signed in", children(browser.root) != null)
        controller.transportControls.playFromSearch("", Bundle())
        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
    }

    @Test
    fun customActionsFavoriteShuffleAndRepeatTheCurrentTrack() {
        assumeTrue("Not signed in", children(browser.root) != null)
        controller.transportControls.playFromSearch("", Bundle())
        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
        assertNotNull("No favorite action", awaitCustomAction(FAVORITE))
        assertNotNull("No album favorite action", awaitCustomAction(ALBUM_FAVORITE))
        assertNotNull("No artist favorite action", awaitCustomAction(ARTIST_FAVORITE))

        controller.transportControls.sendCustomAction(SHUFFLE, null)
        assertNotNull("Shuffle didn't turn on", awaitCustomAction(SHUFFLE) { it.name == label("action_shuffle_on") })
        controller.transportControls.sendCustomAction(SHUFFLE, null)
        assertNotNull("Shuffle didn't turn off", awaitCustomAction(SHUFFLE) { it.name == label("action_shuffle_off") })

        controller.transportControls.sendCustomAction(REPEAT, null)
        assertNotNull("Repeat didn't cycle to all", awaitCustomAction(REPEAT) { it.name == label("action_repeat_all") })
        controller.transportControls.sendCustomAction(REPEAT, null)
        controller.transportControls.sendCustomAction(REPEAT, null)
        assertNotNull("Repeat didn't cycle back to off", awaitCustomAction(REPEAT) { it.name == label("action_repeat_off") })
    }

    private fun awaitCustomAction(
        action: String,
        matches: (PlaybackState.CustomAction) -> Boolean = { true },
    ): PlaybackState.CustomAction? {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        while (System.currentTimeMillis() < deadline) {
            controller.playbackState?.customActions?.firstOrNull { it.action == action && matches(it) }
                ?.let { return it }
            Thread.sleep(250)
        }
        return null
    }

    private fun firstArtistName(): String? =
        firstChild(TAB_LIBRARY, "library_artists")?.description?.title?.toString()

    /** The first item below the root's [path] (media ids, e.g. `tab_library`, `library_albums`), if any. */
    private fun firstChild(vararg path: String): MediaBrowser.MediaItem? {
        var parentId = browser.root
        for (id in path) {
            parentId = children(parentId)?.firstOrNull { it.mediaId == id }?.mediaId ?: return null
        }
        return children(parentId)?.firstOrNull()
    }

    /** The app's text [name] in the device's language, as the media center shows it. */
    private fun label(name: String): String =
        context.getString(context.resources.getIdentifier(name, "string", context.packageName))

    /** Waits for playback and checks that the current item's [key] is [expected]. */
    private fun assertPlaying(key: String, expected: String) {
        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
        val playing = controller.metadata?.getString(key).orEmpty()
        assertTrue("Playing \"$playing\" for \"$expected\"", playing.equals(expected, ignoreCase = true))
    }

    /** The children of [parentId], or `null` if the service answered with an error (e.g. signed out). */
    private fun children(parentId: String): List<MediaBrowser.MediaItem>? {
        val latch = CountDownLatch(1)
        var result: List<MediaBrowser.MediaItem>? = null
        val callback = object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaBrowser.MediaItem>) {
                result = children
                latch.countDown()
            }

            override fun onError(parentId: String) = latch.countDown()
        }
        main.post { browser.subscribe(parentId, callback) }
        latch.await(TIMEOUT_S, TimeUnit.SECONDS)
        main.post { browser.unsubscribe(parentId, callback) }
        return result
    }

    private fun awaitState(expected: Int): Int? {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        while (System.currentTimeMillis() < deadline) {
            val state = controller.playbackState?.state
            if (state == expected) return state
            Thread.sleep(250)
        }
        return controller.playbackState?.state
    }

    private companion object {
        const val TIMEOUT_S = 20L
        const val TAB_LIBRARY = "tab_library"
        const val TAB_PLAYLISTS = "tab_playlists"
        const val UNKNOWN = "Zzyzx Qwfp Nonexistent"
        const val FAVORITE = "dev.harlow.action.TOGGLE_FAVORITE"
        const val ALBUM_FAVORITE = "dev.harlow.action.TOGGLE_ALBUM_FAVORITE"
        const val ARTIST_FAVORITE = "dev.harlow.action.TOGGLE_ARTIST_FAVORITE"
        const val SHUFFLE = "dev.harlow.action.TOGGLE_SHUFFLE"
        const val REPEAT = "dev.harlow.action.CYCLE_REPEAT"
    }
}
