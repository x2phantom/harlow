package dev.harlow.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.harlow.core.data.ArtworkPreCacher
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.core.data.preferences.DisplayPreferences
import dev.harlow.core.data.preferences.PlaybackPreferences
import dev.harlow.core.data.preferences.PlaybackQueuePreferences
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.data.playback.JellyfinPlaybackReporter
import dev.harlow.core.data.repository.LibraryRepository
import dev.harlow.core.data.repository.LibraryRepositoryImpl
import dev.harlow.core.data.repository.PlaylistRepository
import dev.harlow.core.data.repository.PlaylistRepositoryImpl
import dev.harlow.core.data.repository.UserRepository
import dev.harlow.core.data.repository.UserRepositoryImpl
import dev.harlow.core.common.PlaybackReporter

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindPlaybackReporter(impl: JellyfinPlaybackReporter): PlaybackReporter

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @IntoSet
    abstract fun bindArtworkCleanup(impl: ArtworkPreCacher): SignOutCleanup

    @Binds
    @IntoSet
    abstract fun bindQueueCleanup(impl: PlaybackQueuePreferences): SignOutCleanup

    @Binds
    @IntoSet
    abstract fun bindPlaybackPreferencesCleanup(impl: PlaybackPreferences): SignOutCleanup

    @Binds
    @IntoSet
    abstract fun bindSyncPreferencesCleanup(impl: SyncPreferences): SignOutCleanup

    @Binds
    @IntoSet
    abstract fun bindDisplayPreferencesCleanup(impl: DisplayPreferences): SignOutCleanup
}
