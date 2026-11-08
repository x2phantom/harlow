package dev.harlow.core.data

/**
 * Something that holds data of the signed-in user outside Room (preferences, caches, downloads, queued work): removed
 * when signing out (`UserRepositoryImpl.logout`). Bound with Hilt's `@IntoSet`, also by the modules core/data can't
 * see (player, sync).
 * Signing out has to leave the app as after a fresh install; a failing cleanup must not stop the others.
 */
interface SignOutCleanup {
    /** Runs first, before anything is deleted: stop work that could write data again (sync, playback). */
    suspend fun stopBeforeSignOut() {}

    /** Deletes this component's data. */
    suspend fun clearOnSignOut()
}
