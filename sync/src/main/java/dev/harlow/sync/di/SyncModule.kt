package dev.harlow.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.harlow.core.data.SignOutCleanup
import dev.harlow.sync.SyncScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @IntoSet
    abstract fun bindSyncCleanup(impl: SyncScheduler): SignOutCleanup
}
