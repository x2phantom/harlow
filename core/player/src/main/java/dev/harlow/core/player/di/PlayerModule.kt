package dev.harlow.core.player.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.core.network.JellyfinClientWrapper
import dev.harlow.core.player.cache.HarlowCache
import dev.harlow.core.player.cache.HarlowDataSourceFactory
import dev.harlow.core.player.cache.PlayerSignOutCleanup
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideHarlowCache(@ApplicationContext context: Context): HarlowCache =
        HarlowCache(context)

    @Provides
    @Singleton
    fun provideHarlowDataSourceFactory(
        cache: HarlowCache,
        jellyfinClient: JellyfinClientWrapper,
    ): HarlowDataSourceFactory =
        HarlowDataSourceFactory(cache, jellyfinClient)

    @Provides
    @IntoSet
    fun providePlayerCleanup(impl: PlayerSignOutCleanup): SignOutCleanup = impl
}
