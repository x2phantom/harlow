package androidx.media3.session

/**
 * Puts [error] as a non-fatal error into the platform session's playback state and keeps it there until
 * [MediaLibraryService.MediaLibrarySession.clearReplicatedLibraryError]. `sendError` removes it again right away,
 * and the car's media center cancels its toast with the next playback state, so the message flashes unreadably.
 *
 * Lives in Media3's package for the package-private legacy stub; Media3 has no public API for it. Main thread.
 */
internal fun MediaLibraryService.MediaLibrarySession.setLegacyNotice(error: SessionError) {
    getImpl().mediaSessionLegacyStub.setLegacyError(LibraryResult.ofError<Any>(error), false)
}
