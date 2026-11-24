package dev.harlow.core.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import androidx.media3.session.legacyErrorMessage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionNoticesTest {

    private lateinit var session: MediaLibrarySession
    private lateinit var notices: SessionNotices

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        session = MediaLibrarySession.Builder(context, IdlePlayer(), object : MediaLibrarySession.Callback {}).build()
        notices = SessionNotices(session, Handler(Looper.getMainLooper()))
        idle()
    }

    @After
    fun tearDown() {
        session.release()
        session.player.release()
    }

    @Test
    fun `keeps the notice in the platform playback state for five seconds`() {
        notices.show(SessionError(SessionError.ERROR_IO, "offline"))
        idle()
        assertEquals("offline", session.legacyErrorMessage())

        idle(Duration.ofMillis(SessionNotices.DURATION_MS - 100))
        assertEquals("offline", session.legacyErrorMessage())

        idle(Duration.ofMillis(200))
        assertNull(session.legacyErrorMessage())
    }

    @Test
    fun `a repeated notice starts over and shows the new message`() {
        notices.show(SessionError(SessionError.ERROR_IO, "first"))
        idle(Duration.ofMillis(3_000))
        notices.show(SessionError(SessionError.ERROR_IO, "second"))
        idle(Duration.ofMillis(3_000))
        assertEquals("second", session.legacyErrorMessage())

        idle(Duration.ofMillis(2_100))
        assertNull(session.legacyErrorMessage())
    }

    @Test
    fun `clear removes the notice at once`() {
        notices.show(SessionError(SessionError.ERROR_IO, "offline"))
        idle()
        notices.clear()
        idle()
        assertNull(session.legacyErrorMessage())
    }

    private fun idle(duration: Duration = Duration.ZERO) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
    }

    private class IdlePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .build()

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
