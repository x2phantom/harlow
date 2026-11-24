package androidx.media3.session

/** Error message of the platform session's playback state, as the car's media center (a legacy controller) sees it. */
@Suppress("RestrictedApi")
internal fun MediaLibraryService.MediaLibrarySession.legacyErrorMessage(): CharSequence? =
    getImpl().mediaSessionLegacyStub.sessionCompat.controller.playbackState?.errorMessage
