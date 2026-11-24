package dev.harlow.core.player

import dev.harlow.core.database.dao.TrackDao
import dev.harlow.core.database.dao.getTracksById
import dev.harlow.core.database.entity.TrackEntity

/**
 * The media center's "Tracks" list: the library by sort name, at most [limit] tracks. Offline only the [available]
 * ones, looked up directly: filtering the first [limit] of a large library would miss most of them.
 */
internal suspend fun TrackDao.librarySongs(serverId: String, available: Set<String>?, limit: Int): List<TrackEntity> =
    if (available == null) {
        getTracksByServerPaged(serverId, limit = limit, offset = 0)
    } else {
        getTracksById(available).values
            .filter { it.serverId == serverId }
            .sortedBy { it.sortName }
            .take(limit)
    }

/** SQLite's `LIMIT -1`: no limit. */
private const val NO_LIMIT = -1

/**
 * A limited list ([query] with [limit]) such as a search or a home row. Offline ([isAvailable] set) the query runs
 * without a limit and only then is cut to [limit] among the available items, as filtering the limited result would
 * drop available items beyond it. Order and criteria stay those of the query.
 */
internal suspend fun <T> availableFirst(
    limit: Int,
    isAvailable: ((T) -> Boolean)?,
    query: suspend (limit: Int) -> List<T>,
): List<T> = if (isAvailable == null) query(limit) else query(NO_LIMIT).filter(isAvailable).take(limit)
