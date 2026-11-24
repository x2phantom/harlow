package dev.harlow.core.player.cache

import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.IOException
import java.nio.file.Files
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackCacheCompleterTest {

    private lateinit var dir: File
    private lateinit var cache: SimpleCache
    private lateinit var cachedTracks: CachedTracks
    private val server = FakeServer()

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
    fun `fetches the segments a skipped transcoded track is missing`() {
        val segments = mapOf(
            segmentKey("-1", 0) to ByteArray(700) { 1 },
            segmentKey("0", 0) to ByteArray(1_000) { 2 },
            segmentKey("1", 30_000_000) to ByteArray(1_000) { 3 },
        )
        server.files += segments + (hls.uri.toString() to playlist())
        // Played for a moment: playlist, init segment and the first segment are cached.
        put(hls.uri.toString(), playlist())
        put(segmentKey("-1", 0), segments.getValue(segmentKey("-1", 0)))
        put(segmentKey("0", 0), segments.getValue(segmentKey("0", 0)))
        assertFalse(cachedTracks.isComplete(hls))

        completer().completeNow(hls)

        assertTrue(cachedTracks.isComplete(hls))
        assertEquals(listOf(segmentKey("1", 30_000_000)), server.requested)
    }

    @Test
    fun `fetches the rest of an original file`() {
        val file = ByteArray(5_000) { it.toByte() }
        server.files[original.uri.toString()] = file
        put(trackId, file.copyOf(2_000), length = null)
        assertFalse(cachedTracks.isComplete(original))

        completer().completeNow(original)

        assertTrue(cachedTracks.isComplete(original))
    }

    @Test
    fun `a complete track isn't fetched again`() {
        put(trackId, ByteArray(5_000))

        completer().completeNow(original)

        assertTrue(server.requested.isEmpty())
    }

    @Test
    fun `without the server the track stays incomplete and nothing throws`() {
        put(trackId, ByteArray(2_000), length = null)

        completer().completeNow(original)

        assertFalse(cachedTracks.isComplete(original))
    }

    private fun completer() = TrackCacheCompleter(cache, server)

    /** Writes [data] under [key] like the player's CacheDataSource; the length is known once read to the end. */
    private fun put(key: String, data: ByteArray, length: Int? = data.size) {
        val hole = cache.startReadWrite(key, 0, data.size.toLong())!!
        val sink = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
        sink.open(DataSpec.Builder().setUri(key).setKey(key).setLength(data.size.toLong()).build())
        sink.write(data, 0, data.size)
        sink.close()
        cache.releaseHoleSpan(hole)
        if (length != null) {
            val mutations = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(mutations, length.toLong())
            cache.applyContentMetadataMutations(key, mutations)
        }
    }

    /** Serves [files] by URL and records what was requested; anything else fails like an unreachable server. */
    private class FakeServer : DataSource.Factory {
        val files = mutableMapOf<String, ByteArray>()
        val requested = mutableListOf<String>()

        override fun createDataSource(): DataSource = object : DataSource {
            private var delegate: ByteArrayDataSource? = null
            private var uri: Uri? = null

            override fun addTransferListener(transferListener: TransferListener) = Unit

            override fun open(dataSpec: DataSpec): Long {
                val url = dataSpec.uri.toString()
                val data = files[url] ?: throw IOException("Unreachable")
                requested += url
                uri = dataSpec.uri
                return ByteArrayDataSource(data).also { delegate = it }.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate!!.read(buffer, offset, length)

            override fun getUri(): Uri? = uri

            override fun close() {
                delegate?.close()
                delegate = null
            }
        }
    }

    private val query = "segmentContainer=mp4&transcodingContainer=mp4&audioCodec=aac&audioBitRate=320000" +
        "&mediaSourceId=bf45c438269b5b6941df366141947cc2&PlaySessionId=bf45c438269b5b6941df366141947cc2-320000"

    private fun segmentKey(index: String, runtimeTicks: Long): String {
        val length = if (index == "-1") 0 else 30_000_000
        return "$SERVER/Audio/$trackId/hls1/main/$index.mp4?$query&runtimeTicks=$runtimeTicks" +
            "&actualSegmentLengthTicks=$length"
    }

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
