package dev.harlow.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class PeriodicSyncRequestTest {

    /**
     * Signing in schedules the periodic sync and starts a sync right away (`syncNow`). A periodic request without an
     * initial delay runs at once too, so the first sync of a library ran twice, in parallel (seen on the emulator
     * with 4,610 tracks: two workers, both with the same progress, twice the traffic).
     */
    @Test
    fun firstPeriodicSyncWaitsOneInterval() {
        val request = SyncScheduler.periodicSyncRequest("server", intervalHours = 6)

        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.initialDelay)
        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.intervalDuration)
    }
}
