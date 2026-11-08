package dev.harlow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.harlow.core.database.converter.Converters
import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistAliasDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.DownloadDao
import dev.harlow.core.database.dao.PendingPlaybackEventDao
import dev.harlow.core.database.dao.PlaylistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.ArtistAliasEntity
import dev.harlow.core.database.entity.ArtistEntity
import dev.harlow.core.database.entity.DownloadEntity
import dev.harlow.core.database.entity.PendingPlaybackEventEntity
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.PlaylistTrackCrossRef
import dev.harlow.core.database.entity.AlbumArtistCrossRef
import dev.harlow.core.database.entity.TrackArtistCrossRef
import dev.harlow.core.database.entity.ServerEntity
import dev.harlow.core.database.entity.TrackEntity

@Database(
    entities = [
        ServerEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ArtistAliasEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        AlbumArtistCrossRef::class,
        TrackArtistCrossRef::class,
        PendingPlaybackEventEntity::class,
        DownloadEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HarlowDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun artistAliasDao(): ArtistAliasDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pendingPlaybackEventDao(): PendingPlaybackEventDao
    abstract fun downloadDao(): DownloadDao
}
