package dev.harlow.core.data.mapper

import dev.harlow.core.database.entity.AlbumEntity
import dev.harlow.core.database.entity.ArtistEntity
import dev.harlow.core.database.entity.PlaylistEntity
import dev.harlow.core.database.entity.TrackEntity
import dev.harlow.core.model.Album
import dev.harlow.core.model.Artist
import dev.harlow.core.model.Playlist
import dev.harlow.core.model.Track
import java.time.Duration

fun AlbumEntity.toModel() = Album(
    id = id,
    name = name,
    artistId = artistId,
    resolvedArtistId = resolvedArtistId,
    artistName = artistName,
    year = year,
    trackCount = trackCount,
    genres = genres,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    dateAdded = dateAdded,
)

fun ArtistEntity.toModel() = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    overview = overview,
    genres = genres,
)

fun TrackEntity.toModel() = Track(
    id = id,
    name = name,
    albumId = albumId,
    albumName = albumName,
    artistId = artistId,
    resolvedArtistId = resolvedArtistId,
    artistName = artistName,
    trackNumber = trackNumber,
    discNumber = discNumber,
    duration = Duration.ofMillis(durationMs),
    genres = genres,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    normalizationGain = normalizationGain,
    codec = codec,
    container = container,
    dateAdded = dateAdded,
)

fun PlaylistEntity.toModel() = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    durationMs = durationMs,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    isLocal = isLocal,
)
