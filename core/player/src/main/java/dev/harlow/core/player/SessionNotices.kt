package dev.harlow.core.player

import android.os.Handler
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.setLegacyNotice

/**
 * Tells controllers why an action failed, as a non-fatal session error. Media3 controllers get it as `onError`; for
 * the car's media center (a legacy controller) the error stays [DURATION_MS] in the platform playback state, as the
 * media center shows it as a toast only while it is there (Media3's `sendError` clears it at once). A successful
 * library request clears it earlier, as does a new notice. Main thread.
 */
internal class SessionNotices(
    private val session: MediaLibrarySession,
    private val handler: Handler,
) {
    private var held = false
    private val clear = Runnable { clear() }

    /** Shows [error] to [controller], or to all controllers if null. */
    fun show(error: SessionError, controller: MediaSession.ControllerInfo? = null) {
        session.connectedControllers
            .filter { controller == null || it == controller }
            .filter { !it.isLegacy() && !session.isMediaNotificationController(it) }
            .forEach { session.sendError(it, error) }
        if (controller != null && !controller.isLegacy()) return
        // Media3 ignores a legacy error with the code of the one already set, so a repeated notice starts over.
        if (held) session.clearReplicatedLibraryError()
        session.setLegacyNotice(error)
        held = true
        handler.removeCallbacks(clear)
        handler.postDelayed(clear, DURATION_MS)
    }

    /** Removes a notice still shown; leaves the playback state alone otherwise (e.g. a replicated sign-in error). */
    fun clear() {
        handler.removeCallbacks(clear)
        if (!held) return
        held = false
        session.clearReplicatedLibraryError()
    }

    private fun MediaSession.ControllerInfo.isLegacy() =
        controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION

    companion object {
        /** Long enough to read the message at arm's length (OEM review: the toast vanished unreadably). */
        const val DURATION_MS = 5_000L
    }
}
