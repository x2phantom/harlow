package dev.harlow.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.harlow.core.database.HarlowDatabase
import dev.harlow.core.database.KeystoreTokenCipher
import dev.harlow.core.database.TokenCipher
import dev.harlow.core.database.migration.Migrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HarlowDatabase =
        Room.databaseBuilder(
            context,
            HarlowDatabase::class.java,
            "harlow.db",
        )
            .addMigrations(
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7,
                Migrations.MIGRATION_7_8,
                Migrations.MIGRATION_8_9,
                Migrations.MIGRATION_9_10,
                Migrations.MIGRATION_10_11,
                Migrations.MIGRATION_11_12,
            )
            .build()

    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

    @Provides
    fun provideServerDao(db: HarlowDatabase) = db.serverDao()

    @Provides
    fun provideAlbumDao(db: HarlowDatabase) = db.albumDao()

    @Provides
    fun provideArtistDao(db: HarlowDatabase) = db.artistDao()

    @Provides
    fun provideArtistAliasDao(db: HarlowDatabase) = db.artistAliasDao()

    @Provides
    fun provideTrackDao(db: HarlowDatabase) = db.trackDao()

    @Provides
    fun providePlaylistDao(db: HarlowDatabase) = db.playlistDao()

    @Provides
    fun providePendingPlaybackEventDao(db: HarlowDatabase) = db.pendingPlaybackEventDao()

    @Provides
    fun provideDownloadDao(db: HarlowDatabase) = db.downloadDao()


}
