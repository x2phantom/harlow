package dev.harlow.core.player.cache

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedTracksTest {

    private lateinit var dir: File
    private lateinit var cache: SimpleCache
    private lateinit var cachedTracks: CachedTracks

    private val trackId = "bf45c438-269b-5b69-41df-366141947cc2"
    private val hls = streamSource(SERVER, trackId, transcodeBitrate = 320_000)
    private val original = streamSource(SERVER, trackId, transcodeBitrate = null)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cache").toFile()
        cache = SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))
        cachedTracks = CachedTracks(cache)
    }

    @After
    fun tearDown() {
        cache.release()
        dir.deleteRecursively()
    }

    @Test
    fun `a transcoded track is complete with its playlist, init segment and all segments`() {
        put(hls.uri.toString(), playlist())
        put(segmentKey("-1", 0), ByteArray(700))
        put(segmentKey("0", 0), ByteArray(1_000))
        put(segmentKey("1", 30_000_000), ByteArray(1_000))

        assertTrue(cachedTracks.isComplete(hls))
        assertEquals(setOf(trackId), cachedTracks.candidateTrackIds())
    }

    @Test
    fun `a transcoded track with a missing segment is not complete`() {
        put(hls.uri.toString(), playlist())
        put(segmentKey("-1", 0), ByteArray(700))
        put(segmentKey("0", 0), ByteArray(1_000))

        assertFalse(cachedTracks.isComplete(hls))
        assertEquals(setOf(trackId), cachedTracks.candidateTrackIds())
    }

    @Test
    fun `a segment read only partly is not complete`() {
        put(hls.uri.toString(), playlist())
        put(segmentKey("-1", 0), ByteArray(700))
        put(segmentKey("0", 0), ByteArray(1_000))
        put(segmentKey("1", 30_000_000), ByteArray(400), length = 1_000)

        assertFalse(cachedTracks.isComplete(hls))
    }

    @Test
    fun `segments at another bitrate don't count`() {
        put(hls.uri.toString(), playlist())
        put(segmentKey("-1", 0), ByteArray(700))
        put(segmentKey("0", 0), ByteArray(1_000))
        put(segmentKey("1", 30_000_000), ByteArray(1_000))

        assertFalse(cachedTracks.isComplete(streamSource(SERVER, trackId, transcodeBitrate = 128_000)))
    }

    @Test
    fun `an original file is complete when all of it is cached under the track ID`() {
        put(trackId, ByteArray(5_000))

        assertTrue(cachedTracks.isComplete(original))
        assertFalse(cachedTracks.isComplete(hls))
        assertEquals(setOf(trackId), cachedTracks.candidateTrackIds())
    }

    @Test
    fun `nothing cached`() {
        assertFalse(cachedTracks.isComplete(hls))
        assertFalse(cachedTracks.isComplete(original))
        assertTrue(cachedTracks.candidateTrackIds().isEmpty())
    }

    /** Writes [data] under [key] like the player's CacheDataSource, which records the length once it is known. */
    private fun put(key: String, data: ByteArray, length: Int = data.size) {
        val hole = cache.startReadWrite(key, 0, data.size.toLong())!!
        val sink = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
        sink.open(DataSpec.Builder().setUri(key).setKey(key).setLength(data.size.toLong()).build())
        sink.write(data, 0, data.size)
        sink.close()
        cache.releaseHoleSpan(hole)
        val mutations = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(mutations, length.toLong())
        cache.applyContentMetadataMutations(key, mutations)
    }

    private val query = "segmentContainer=mp4&transcodingContainer=mp4&audioCodec=aac&audioBitRate=320000" +
        "&mediaSourceId=bf45c438269b5b6941df366141947cc2&PlaySessionId=bf45c438269b5b6941df366141947cc2-320000"

    private fun segmentKey(index: String, runtimeTicks: Long): String {
        val length = if (index == "-1") 0 else 30_000_000
        return "$SERVER/Audio/$trackId/hls1/main/$index.mp4?$query&runtimeTicks=$runtimeTicks" +
            "&actualSegmentLengthTicks=$length"
    }

    /** A media playlist as Jellyfin 12 sends it for a 6-second track (relative segment URLs). */
    private fun playlist(): ByteArray = """
        #EXTM3U
        #EXT-X-PLAYLIST-TYPE:VOD
        #EXT-X-VERSION:7
        #EXT-X-TARGETDURATION:3
        #EXT-X-MEDIA-SEQUENCE:0
        #EXT-X-MAP:URI="hls1/main/-1.mp4?$query&runtimeTicks=0&actualSegmentLengthTicks=0"
        #EXTINF:3.000000, nodesc
        hls1/main/0.mp4?$query&runtimeTicks=0&actualSegmentLengthTicks=30000000
        #EXTINF:3.000000, nodesc
        hls1/main/1.mp4?$query&runtimeTicks=30000000&actualSegmentLengthTicks=30000000
        #EXT-X-ENDLIST
    """.trimIndent().toByteArray()

    private companion object {
        const val SERVER = "https://jelly.example.com"
    }
}
