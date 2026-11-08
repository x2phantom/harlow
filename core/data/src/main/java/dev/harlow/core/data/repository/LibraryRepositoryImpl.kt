package dev.harlow.core.data.repository

import android.util.Log
import dev.harlow.core.common.HarlowResult
import dev.harlow.core.data.SyncProgress
import dev.harlow.core.data.mapper.toAlbumArtistCrossRefs
import dev.harlow.core.data.mapper.toAlbumEntity
import dev.harlow.core.data.mapper.toArtistEntity
import dev.harlow.core.data.mapper.toModel
import dev.harlow.core.data.mapper.toTrackArtistCrossRefs
import dev.harlow.core.data.mapper.toTrackEntity
import dev.harlow.core.data.preferences.SyncPreferences
import dev.harlow.core.common.getCleanValue
import dev.harlow.core.database.dao.AlbumDao
import dev.harlow.core.database.dao.ArtistAliasDao
import dev.harlow.core.database.dao.ArtistDao
import dev.harlow.core.database.dao.ServerDao
import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.dao.getInstantMix
import dev.harlow.core.database.entity.ArtistAliasEntity
import dev.harlow.core.database.entity.ArtistEntity
import dev.harlow.core.model.Album
import dev.harlow.core.model.Artist
import dev.harlow.core.model.Track
import dev.harlow.core.network.datasource.JellyfinDataSource
import dev.harlow.core.network.datasource.PagedItems
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val artistAliasDao: ArtistAliasDao,
    private val trackDao: TrackDao,
    private val serverDao: ServerDao,
    private val jellyfinDataSource: JellyfinDataSource,
    private val syncPreferences: SyncPreferences,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
        private const val ROOM_BIND_LIMIT = 900
    }

    override fun getAlbums(serverId: String): Flow<HarlowResult<List<Album>>> =
        albumDao.observeAlbumsByServer(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Album>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getArtists(serverId: String): Flow<HarlowResult<List<Artist>>> =
        artistDao.observeArtistsByServer(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Artist>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getAlbumTracks(albumId: String): Flow<HarlowResult<List<Track>>> =
        trackDao.getTracksByAlbum(albumId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Track>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getArtistAlbumsById(artistId: String): Flow<HarlowResult<List<Album>>> =
        albumDao.getAlbumsByResolvedArtist(artistId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Album>> }
            .catch { emit(HarlowResult.Error(it)) }

    override suspend fun getAlbum(albumId: String): HarlowResult<Album?> =
        try {
            HarlowResult.Success(albumDao.getAlbumById(albumId)?.toModel())
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override fun observeAlbum(albumId: String): Flow<HarlowResult<Album?>> =
        albumDao.observeAlbumById(albumId)
            .map { HarlowResult.Success(it?.toModel()) as HarlowResult<Album?> }
            .catch { emit(HarlowResult.Error(it)) }

    override suspend fun getArtist(artistId: String): HarlowResult<Artist?> =
        try {
            HarlowResult.Success(artistDao.getArtistById(artistId)?.toModel())
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override fun observeArtist(artistId: String): Flow<HarlowResult<Artist?>> =
        artistDao.observeArtistById(artistId)
            .map { HarlowResult.Success(it?.toModel()) as HarlowResult<Artist?> }
            .catch { emit(HarlowResult.Error(it)) }


    override fun getArtistTracksById(artistId: String): Flow<HarlowResult<List<Track>>> =
        trackDao.getTracksByResolvedArtist(artistId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Track>> }
            .catch { emit(HarlowResult.Error(it)) }

    override suspend fun countArtistAlbumsById(artistId: String): HarlowResult<Int> =
        try {
            HarlowResult.Success(albumDao.countAlbumsByResolvedArtist(artistId))
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override suspend fun countArtistTracksById(artistId: String): HarlowResult<Int> =
        try {
            HarlowResult.Success(trackDao.countTracksByResolvedArtist(artistId))
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override fun getRecentTracks(serverId: String): Flow<HarlowResult<List<Track>>> =
        trackDao.observeRecentTracks(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Track>> }
            .catch { emit(HarlowResult.Error(it)) }

    override suspend fun search(serverId: String, query: String): HarlowResult<List<Track>> =
        try {
            HarlowResult.Success(trackDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override suspend fun searchAlbums(serverId: String, query: String): HarlowResult<List<Album>> =
        try {
            HarlowResult.Success(albumDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override suspend fun searchArtists(serverId: String, query: String): HarlowResult<List<Artist>> =
        try {
            HarlowResult.Success(artistDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }

    override fun getFavoriteTracks(serverId: String): Flow<HarlowResult<List<Track>>> =
        trackDao.getFavoriteTracks(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Track>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getFavoriteAlbums(serverId: String): Flow<HarlowResult<List<Album>>> =
        albumDao.getFavoriteAlbums(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Album>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getFavoriteArtists(serverId: String): Flow<HarlowResult<List<Artist>>> =
        artistDao.getFavoriteArtists(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Artist>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getRecentlyPlayedAlbums(serverId: String): Flow<HarlowResult<List<Album>>> =
        albumDao.getRecentlyPlayedAlbums(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Album>> }
            .catch { emit(HarlowResult.Error(it)) }

    override fun getMostPlayedAlbums(serverId: String): Flow<HarlowResult<List<Album>>> =
        albumDao.getMostPlayedAlbums(serverId)
            .map { entities -> HarlowResult.Success(entities.map { it.toModel() }) as HarlowResult<List<Album>> }
            .catch { emit(HarlowResult.Error(it)) }

    override suspend fun syncHomeScreenPriority(
        serverId: String,
        onProgress: (SyncProgress) -> Unit,
    ): HarlowResult<Set<String>> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(emptySet())
            val userId = UUID.fromString(server.userId)
            val imageIds = mutableSetOf<String>()

            onProgress(SyncProgress("home", 0, 5))

            coroutineScope {
                val recentAlbums = async { jellyfinDataSource.getRecentlyAddedAlbums(userId, 50) }
                val recentTracks = async { jellyfinDataSource.getRecentlyPlayedItems(userId, 200) }
                val favAlbums = async { jellyfinDataSource.getFavoriteAlbums(userId) }
                val favTracks = async { jellyfinDataSource.getFavoriteTracks(userId) }

                val albums = recentAlbums.await()
                albumDao.upsertAlbums(albums.map { it.toAlbumEntity(serverId) })
                albumDao.insertAlbumArtists(albums.flatMap { it.toAlbumArtistCrossRefs() })
                albums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 1, 5))

                val tracks = recentTracks.await()
                trackDao.upsertTracks(tracks.map { it.toTrackEntity(serverId) })
                trackDao.insertTrackArtists(tracks.flatMap { it.toTrackArtistCrossRefs() })
                tracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 2, 5))

                val fAlbums = favAlbums.await()
                albumDao.upsertAlbums(fAlbums.map { it.toAlbumEntity(serverId) })
                albumDao.insertAlbumArtists(fAlbums.flatMap { it.toAlbumArtistCrossRefs() })
                fAlbums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 3, 5))

                val fTracks = favTracks.await()
                trackDao.upsertTracks(fTracks.map { it.toTrackEntity(serverId) })
                trackDao.insertTrackArtists(fTracks.flatMap { it.toTrackArtistCrossRefs() })
                fTracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 4, 5))

                val knownAlbumIds = albums.map { it.id.toString() }.toSet() +
                    fAlbums.map { it.id.toString() }.toSet()
                val missingAlbumIds = (tracks + fTracks).mapNotNull { it.albumId }
                    .distinct()
                    .filter { it.toString() !in knownAlbumIds }
                if (missingAlbumIds.isNotEmpty()) {
                    val parentAlbums = jellyfinDataSource.getAlbumsByIds(userId, missingAlbumIds)
                    albumDao.upsertAlbums(parentAlbums.map { it.toAlbumEntity(serverId) })
                    albumDao.insertAlbumArtists(parentAlbums.flatMap { it.toAlbumArtistCrossRefs() })
                    parentAlbums.forEach { it.id.toString().let(imageIds::add) }
                }
                onProgress(SyncProgress("home", 5, 5))
            }

            Log.d(TAG, "Home screen priority sync: ${imageIds.size} unique image IDs")
            HarlowResult.Success(imageIds)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            val lastSyncMs = syncPreferences.lastSyncTimestamp.first()

            if (lastSyncMs == 0L) {
                Log.d(TAG, "First sync — full")
                fullSync(serverId, userId, onProgress)
            } else {
                val albumsOutdated = syncPreferences.albumRevision.first() < SyncPreferences.CURRENT_ALBUM_REVISION
                val tracksOutdated = syncPreferences.trackRevision.first() < SyncPreferences.CURRENT_TRACK_REVISION

                if (albumsOutdated || tracksOutdated) {
                    Log.d(TAG, "Data revision outdated (albums=$albumsOutdated, tracks=$tracksOutdated)")
                }

                val since = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncMs), ZoneOffset.UTC)
                syncAllArtists(serverId, userId, onProgress)
                if (albumsOutdated) syncAllAlbums(serverId, userId, onProgress) else syncAlbumsIncremental(serverId, userId, since, onProgress)
                if (tracksOutdated) syncAllTracks(serverId, userId, onProgress) else syncTracksIncremental(serverId, userId, since, onProgress)
            }

            resolveArtistAliases(serverId)
            albumDao.resolveArtistIds(serverId)
            trackDao.resolveArtistIds(serverId)
            albumDao.resolveArtistAliases(serverId)
            trackDao.resolveArtistAliases(serverId)

            onProgress(SyncProgress("favorites", 0, 0))
            syncFavoritesDiff(serverId, userId)
            syncRecentlyPlayed(serverId, userId)

            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            syncPreferences.setAlbumRevision(SyncPreferences.CURRENT_ALBUM_REVISION)
            syncPreferences.setArtistRevision(SyncPreferences.CURRENT_ARTIST_REVISION)
            syncPreferences.setTrackRevision(SyncPreferences.CURRENT_TRACK_REVISION)
            syncPreferences.incrementSyncCount()
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            onProgress(SyncProgress("albums", 0, 0))
            detectOrphanedAlbums(serverId, userId)
            onProgress(SyncProgress("artists", 0, 0))
            detectOrphanedArtists(serverId, userId)
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    override suspend fun syncFavorites(serverId: String): HarlowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return HarlowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            val favAlbums = jellyfinDataSource.getFavoriteAlbums(userId)
            Log.d(TAG, "syncFavorites: ${favAlbums.size} albums from API")
            if (favAlbums.isNotEmpty()) {
                albumDao.upsertAlbums(favAlbums.map { it.toAlbumEntity(serverId) })
                favAlbums.forEach { albumDao.clearAlbumArtists(it.id.toString()) }
                albumDao.insertAlbumArtists(favAlbums.flatMap { it.toAlbumArtistCrossRefs() })
            }

            val favArtists = jellyfinDataSource.getFavoriteArtists(userId)
            Log.d(TAG, "syncFavorites: ${favArtists.size} artists from API")
            if (favArtists.isNotEmpty()) {
                artistDao.upsertArtists(favArtists.map { it.toArtistEntity(serverId) })
            }
            applyFavoriteArtists(serverId, favArtists.map { it.id.toString() }.toSet())

            val favTracks = jellyfinDataSource.getFavoriteTracks(userId)
            Log.d(TAG, "syncFavorites: ${favTracks.size} tracks from API")
            if (favTracks.isNotEmpty()) {
                trackDao.upsertTracks(favTracks.map { it.toTrackEntity(serverId) })
                favTracks.forEach { trackDao.clearTrackArtists(it.id.toString()) }
                trackDao.insertTrackArtists(favTracks.flatMap { it.toTrackArtistCrossRefs() })
            }
            HarlowResult.Success(Unit)
        } catch (e: Exception) {
            HarlowResult.Error(e)
        }
    }

    private suspend fun resolveArtistAliases(serverId: String) {
        val artists = artistDao.getAllArtistsByServer(serverId).map { artist ->
            if (artist.cleanName.isEmpty()) {
                val computed = getCleanValue(artist.name)
                artistDao.updateCleanName(artist.id, computed)
                artist.copy(cleanName = computed)
            } else {
                artist
            }
        }

        val clusters = artists.groupBy { it.cleanName }

        val aliases = mutableListOf<ArtistAliasEntity>()
        val now = System.currentTimeMillis()

        for ((_, group) in clusters) {
            val canonical = group.sortedWith(
                compareByDescending<ArtistEntity> { it.musicBrainzId != null }
                    .thenByDescending { it.imageTag != null }
                    .thenByDescending { it.albumCount }
                    .thenBy { it.id },
            ).first()

            for (artist in group) {
                aliases.add(
                    ArtistAliasEntity(
                        serverId = serverId,
                        rawArtistId = artist.id,
                        canonicalArtistId = canonical.id,
                        lastSynced = now,
                    ),
                )
            }
        }

        val mbidGroups = aliases
            .mapNotNull { alias ->
                artists.find { it.id == alias.rawArtistId }?.musicBrainzId?.let { mbid -> mbid to alias }
            }
            .groupBy { it.first }

        for ((_, mbidAliases) in mbidGroups) {
            if (mbidAliases.size > 1) {
                val canonicalId = mbidAliases
                    .map { it.second.canonicalArtistId }
                    .minByOrNull { id ->
                        val a = artists.find { it.id == id }
                        if (a?.musicBrainzId != null) 0 else 1
                    } ?: continue
                for ((_, alias) in mbidAliases) {
                    val idx = aliases.indexOfFirst {
                        it.rawArtistId == alias.rawArtistId && it.serverId == alias.serverId
                    }
                    if (idx >= 0) {
                        aliases[idx] = aliases[idx].copy(canonicalArtistId = canonicalId)
                    }
                }
            }
        }

        artistAliasDao.deleteByServer(serverId)
        artistAliasDao.upsertAliases(aliases)
    }

    private suspend fun fullSync(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        syncAllArtists(serverId, userId, onProgress)
        syncAllAlbums(serverId, userId, onProgress)
        syncAllTracks(serverId, userId, onProgress)
    }


    private suspend fun syncAlbumsIncremental(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        var startIndex = 0
        val pageSize = 500
        while (true) {
            val items = jellyfinDataSource.getAlbums(userId, startIndex, pageSize, minDateLastSaved = since)
            if (items.isEmpty()) break
            Log.d(TAG, "Incremental album sync: ${items.size} changed items at offset $startIndex")
            albumDao.upsertAlbums(items.map { it.toAlbumEntity(serverId) })
            items.forEach { albumDao.clearAlbumArtists(it.id.toString()) }
            albumDao.insertAlbumArtists(items.flatMap { it.toAlbumArtistCrossRefs() })
            startIndex += items.size
            onProgress(SyncProgress("albums", startIndex, startIndex))
            if (items.size < pageSize) break
        }
    }


    private suspend fun syncTracksIncremental(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        var startIndex = 0
        val pageSize = 1000
        while (true) {
            val items = jellyfinDataSource.getTracks(userId, startIndex, pageSize, minDateLastSaved = since)
            if (items.isEmpty()) break
            Log.d(TAG, "Incremental track sync: ${items.size} changed items at offset $startIndex")
            trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
            items.forEach { trackDao.clearTrackArtists(it.id.toString()) }
            trackDao.insertTrackArtists(items.flatMap { it.toTrackArtistCrossRefs() })
            startIndex += items.size
            onProgress(SyncProgress("tracks", startIndex, startIndex))
            if (items.size < pageSize) break
        }
    }

    /**
     * Makes exactly [serverFavArtistIds] the favorite artists. Returns the added and removed ids.
     */
    private suspend fun applyFavoriteArtists(
        serverId: String,
        serverFavArtistIds: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val localFavArtistIds = artistDao.getFavoriteArtistIds(serverId).toSet()
        val added = serverFavArtistIds - localFavArtistIds
        val removed = localFavArtistIds - serverFavArtistIds
        added.chunked(ROOM_BIND_LIMIT).forEach { chunk -> artistDao.setFavoriteByIds(chunk, true) }
        removed.chunked(ROOM_BIND_LIMIT).forEach { chunk -> artistDao.setFavoriteByIds(chunk, false) }
        return added to removed
    }

    private suspend fun syncFavoritesDiff(serverId: String, userId: UUID) {
        val serverFavAlbumIds = jellyfinDataSource.getFavoriteAlbums(userId).map { it.id.toString() }.toSet()
        val serverFavTrackIds = jellyfinDataSource.getFavoriteTracks(userId).map { it.id.toString() }.toSet()
        val serverFavArtistIds = jellyfinDataSource.getFavoriteArtists(userId).map { it.id.toString() }.toSet()

        val localFavAlbumIds = albumDao.getFavoriteAlbumIds(serverId).toSet()
        val localFavTrackIds = trackDao.getFavoriteTrackIds(serverId).toSet()

        val newFavAlbums = serverFavAlbumIds - localFavAlbumIds
        val removedFavAlbums = localFavAlbumIds - serverFavAlbumIds
        newFavAlbums.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            albumDao.setFavoriteByIds(chunk, true)
        }
        removedFavAlbums.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            albumDao.setFavoriteByIds(chunk, false)
        }

        val newFavTracks = serverFavTrackIds - localFavTrackIds
        val removedFavTracks = localFavTrackIds - serverFavTrackIds
        newFavTracks.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            trackDao.setFavoriteByIds(chunk, true)
        }
        removedFavTracks.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            trackDao.setFavoriteByIds(chunk, false)
        }

        val (newFavArtists, removedFavArtists) = applyFavoriteArtists(serverId, serverFavArtistIds)

        Log.d(
            TAG,
            "Favorites diff: albums +${newFavAlbums.size}/-${removedFavAlbums.size}, " +
                "tracks +${newFavTracks.size}/-${removedFavTracks.size}, " +
                "artists +${newFavArtists.size}/-${removedFavArtists.size}",
        )
    }

    private suspend fun syncRecentlyPlayed(serverId: String, userId: UUID) {
        val recentItems = jellyfinDataSource.getRecentlyPlayedItems(userId, limit = 200)
        if (recentItems.isNotEmpty()) {
            trackDao.upsertTracks(recentItems.map { it.toTrackEntity(serverId) })
            Log.d(TAG, "Recently played sync: ${recentItems.size} tracks updated")
        }
    }

    private suspend fun detectOrphanedAlbums(serverId: String, userId: UUID) {
        val serverAlbumIds = mutableSetOf<String>()
        var startIndex = 0
        while (true) {
            val items = jellyfinDataSource.getAlbums(userId, startIndex, 500)
            if (items.isEmpty()) break
            serverAlbumIds.addAll(items.map { it.id.toString() })
            if (items.size < 500) break
            startIndex += 500
        }
        if (serverAlbumIds.isNotEmpty()) {
            val localAlbumIds = albumDao.getAllAlbumIdsByServer(serverId).toSet()
            val orphanIds = localAlbumIds - serverAlbumIds
            if (orphanIds.isNotEmpty()) {
                orphanIds.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
                    albumDao.deleteByIds(chunk)
                }
                Log.d(TAG, "Orphan detection: removed ${orphanIds.size} albums")
            }
        }
    }

    private suspend fun detectOrphanedArtists(serverId: String, userId: UUID) {
        val serverArtistIds = mutableSetOf<String>()
        var startIndex = 0
        while (true) {
            val items = jellyfinDataSource.getArtists(userId, startIndex, 500)
            if (items.isEmpty()) break
            serverArtistIds.addAll(items.map { it.id.toString() })
            if (items.size < 500) break
            startIndex += 500
        }
        if (serverArtistIds.isNotEmpty()) {
            val localArtistIds = artistDao.getAllArtistIdsByServer(serverId).toSet()
            val orphanIds = localArtistIds - serverArtistIds
            if (orphanIds.isNotEmpty()) {
                orphanIds.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
                    artistDao.deleteByIds(chunk)
                }
                Log.d(TAG, "Orphan detection: removed ${orphanIds.size} artists")
            }
        }
    }

    private suspend fun syncAllAlbums(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) = coroutineScope {
        albumDao.clearAllAlbumArtistsByServer(serverId)
        var startIndex = 0
        val pageSize = 500
        var totalCount = 0
        var prefetch: Deferred<PagedItems>? = async { jellyfinDataSource.getAlbumsPaged(userId, 0, pageSize) }
        while (true) {
            val paged = (prefetch ?: async { jellyfinDataSource.getAlbumsPaged(userId, startIndex, pageSize) }).await()
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            val nextStart = startIndex + paged.items.size
            prefetch = if (paged.items.size == pageSize) async { jellyfinDataSource.getAlbumsPaged(userId, nextStart, pageSize) } else null
            albumDao.upsertAlbums(paged.items.map { it.toAlbumEntity(serverId) })
            albumDao.insertAlbumArtists(paged.items.flatMap { it.toAlbumArtistCrossRefs() })
            startIndex = nextStart
            onProgress(SyncProgress("albums", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }

    private suspend fun syncAllArtists(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) = coroutineScope {
        var startIndex = 0
        val pageSize = 500
        var totalCount = 0
        var prefetch: Deferred<PagedItems>? = async { jellyfinDataSource.getArtistsPaged(userId, 0, pageSize) }
        while (true) {
            val paged = (prefetch ?: async { jellyfinDataSource.getArtistsPaged(userId, startIndex, pageSize) }).await()
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            val nextStart = startIndex + paged.items.size
            prefetch = if (paged.items.size == pageSize) async { jellyfinDataSource.getArtistsPaged(userId, nextStart, pageSize) } else null
            artistDao.upsertArtists(paged.items.map { it.toArtistEntity(serverId) })
            startIndex = nextStart
            onProgress(SyncProgress("artists", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }

    private suspend fun syncAllTracks(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) = coroutineScope {
        trackDao.clearAllTrackArtistsByServer(serverId)
        var startIndex = 0
        val pageSize = 1000
        var totalCount = 0
        var prefetch: Deferred<PagedItems>? = async { jellyfinDataSource.getTracksPaged(userId, 0, pageSize) }
        while (true) {
            val paged = (prefetch ?: async { jellyfinDataSource.getTracksPaged(userId, startIndex, pageSize) }).await()
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            val nextStart = startIndex + paged.items.size
            prefetch = if (paged.items.size == pageSize) async { jellyfinDataSource.getTracksPaged(userId, nextStart, pageSize) } else null
            trackDao.upsertTracks(paged.items.map { it.toTrackEntity(serverId) })
            trackDao.insertTrackArtists(paged.items.flatMap { it.toTrackArtistCrossRefs() })
            startIndex = nextStart
            onProgress(SyncProgress("tracks", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }

    override suspend fun getInstantMix(serverId: String, trackId: String): HarlowResult<List<Track>> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return HarlowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = true))
            val userId = UUID.fromString(server.userId)
            val dtos = jellyfinDataSource.getInstantMixFromSong(
                itemId = UUID.fromString(trackId),
                userId = userId,
            )
            if (dtos.isNotEmpty()) {
                trackDao.upsertTracks(dtos.map { it.toTrackEntity(serverId) })
                HarlowResult.Success(
                    dtos.mapNotNull { dto ->
                        trackDao.getTrackById(dto.id.toString())?.toModel()
                    },
                )
            } else {
                // API returned empty — still online, can stream any local track
                HarlowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = false))
            }
        } catch (e: Exception) {
            Log.d(TAG, "InstantMix API unavailable, falling back to offline mix")
            // Network error — truly offline, only downloaded tracks are playable
            try {
                HarlowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = true))
            } catch (fallbackError: Exception) {
                HarlowResult.Error(fallbackError)
            }
        }
    }

    private suspend fun offlineInstantMix(
        serverId: String,
        trackId: String,
        downloadedOnly: Boolean,
    ): List<Track> {
        val seed = trackDao.getTrackById(trackId) ?: return emptyList()
        return trackDao.getInstantMix(
            serverId = serverId,
            seedTrackId = trackId,
            artistName = seed.artistName,
            genres = seed.genres,
            downloadedOnly = downloadedOnly,
        ).map { it.toModel() }
    }
}
