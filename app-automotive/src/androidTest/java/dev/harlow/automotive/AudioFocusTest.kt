package dev.harlow.automotive

import android.content.ComponentName
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Audio focus rules for car media apps against the running HarlowMediaService: another source takes
 * focus the way a media app, the voice assistant or navigation would. The test runs in the app's process but asks for
 * focus as a client of its own, so the player sees an ordinary focus change. Needs a signed-in server with music.
 *
 * `adb shell am instrument -w --user 10 -e class dev.harlow.automotive.AudioFocusTest
 *   eu.hwld.harlow.test/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var browser: MediaBrowser
    private lateinit var controller: MediaController
    private var otherSource: AudioFocusRequest? = null

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
    fun tearDown() {
        otherSource?.let { audioManager.abandonAudioFocusRequest(it) }
        shell("appops set ${context.packageName} TAKE_AUDIO_FOCUS allow")
        if (::controller.isInitialized) controller.transportControls.pause()
        main.post { browser.disconnect() }
    }

    @Test
    fun permanentLossPausesAndDoesNotResume() {
        startPlayback()

        takeFocus(AudioManager.AUDIOFOCUS_GAIN, AudioAttributes.USAGE_MEDIA)
        assertEquals(PlaybackState.STATE_PAUSED, awaitState(PlaybackState.STATE_PAUSED))

        releaseFocus()
        Thread.sleep(SETTLE_MS)
        assertTrue("Resumed after a permanent loss", controller.playbackState?.state != PlaybackState.STATE_PLAYING)
        assertFalse("Still holds audio focus after a permanent loss", holdsFocus())
    }

    @Test
    fun transientLossPausesAndResumesOnGain() {
        startPlayback()

        takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, AudioAttributes.USAGE_ASSISTANT)
        assertTrue("Kept playing during a transient loss", awaitNotPlaying())

        releaseFocus()
        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
    }

    @Test
    fun navigationPromptDucksWithoutPausing() {
        startPlayback()

        takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        Thread.sleep(SETTLE_MS)
        assertEquals(PlaybackState.STATE_PLAYING, controller.playbackState?.state)

        releaseFocus()
        Thread.sleep(SETTLE_MS)
        assertEquals(PlaybackState.STATE_PLAYING, controller.playbackState?.state)
    }

    /** Give focus back once playback ends. (A user's pause keeps it, as Android's media guidance does.) */
    @Test
    fun endOfQueueReleasesFocus() {
        startPlayback()
        assertTrue("Plays without audio focus", holdsFocus())

        val last = controller.queue?.lastOrNull()
        assumeTrue("No queue", last != null)
        controller.transportControls.skipToQueueItem(last!!.queueId)
        Thread.sleep(SETTLE_MS)
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        assumeTrue("Unknown duration", duration > END_MARGIN_MS)
        controller.transportControls.seekTo(duration - END_MARGIN_MS)

        assertTrue("Still playing after the end of the queue", awaitNotPlaying())
        Thread.sleep(SETTLE_MS)
        assertFalse("Holds audio focus after the end of the queue", holdsFocus())
    }

    @Test
    fun deniedFocusDoesNotPlay() {
        assumeTrue("Not signed in", children(browser.root) != null)
        shell("appops set ${context.packageName} TAKE_AUDIO_FOCUS ignore")

        controller.transportControls.playFromSearch("", Bundle())
        Thread.sleep(SETTLE_MS * 2)
        assertTrue("Plays without audio focus", controller.playbackState?.state != PlaybackState.STATE_PLAYING)
        assertFalse("Holds audio focus although it was denied", holdsFocus())
    }

    private fun startPlayback() {
        assumeTrue("Not signed in", children(browser.root) != null)
        controller.transportControls.playFromSearch("", Bundle())
        assertEquals(PlaybackState.STATE_PLAYING, awaitState(PlaybackState.STATE_PLAYING))
        Thread.sleep(SETTLE_MS)
    }

    /** Takes focus like another app would; the request is a client of its own, not the player's. */
    private fun takeFocus(gain: Int, usage: Int) {
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).build())
            .setOnAudioFocusChangeListener({ }, main)
            .build()
        otherSource = request
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(request))
    }

    private fun releaseFocus() {
        otherSource?.let { audioManager.abandonAudioFocusRequest(it) }
        otherSource = null
    }

    /**
     * Whether the player (Media3's `AudioFocusManager`, not this test's requests) appears in the audio focus stack of
     * `dumpsys audio`.
     */
    private fun holdsFocus(): Boolean {
        val stack = shell("dumpsys audio").substringAfter("Audio Focus stack entries", "").substringBefore("\n\n")
        return stack.lines().any { "pack: ${context.packageName} " in it && "media3" in it }
    }

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun awaitNotPlaying(): Boolean {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        while (System.currentTimeMillis() < deadline) {
            if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return true
            Thread.sleep(250)
        }
        return false
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

    private companion object {
        const val TIMEOUT_S = 20L
        const val SETTLE_MS = 2_000L
        const val END_MARGIN_MS = 3_000L
    }
}
