package dev.harlow.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeardTracksTest {

    private val first = item("a")
    private val second = item("b")

    @Test
    fun `a track left after listening to it counts`() {
        assertEquals(first, heardTrackLeft(position(first, 46_000), position(second, 0)))
    }

    @Test
    fun `a track left when its queue is replaced counts`() {
        assertEquals(first, heardTrackLeft(position(first, 10_000), position(null, 0)))
    }

    @Test
    fun `a track skipped through doesn't count`() {
        assertNull(heardTrackLeft(position(first, MIN_HEARD_MS - 1), position(second, 0)))
    }

    @Test
    fun `seeking within the track or repeating it doesn't count`() {
        assertNull(heardTrackLeft(position(first, 60_000), position(first, 0)))
    }

    @Test
    fun `an item without a stream doesn't count`() {
        assertNull(heardTrackLeft(position(MediaItem.Builder().setMediaId("c").build(), 60_000), position(second, 0)))
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://jelly.example.com/$id").build()

    private fun position(item: MediaItem?, positionMs: Long) =
        Player.PositionInfo(null, 0, item, null, 0, positionMs, positionMs, -1, -1)
}
