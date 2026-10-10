package app.ownbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingProbeTest {
    @Test fun parsesAndroidPingLatency() {
        assertEquals(178.4, PingOutput.latencyMs("64 bytes from 1.2.3.4: icmp_seq=1 ttl=50 time=178.4 ms")!!, 0.001)
        assertEquals(0.5, PingOutput.latencyMs("64 bytes from ::1: time<1 ms")!!, 0.001)
        assertNull(PingOutput.latencyMs("100% packet loss"))
    }

    @Test fun averageUsesOnlyReplies() {
        val summary = PingSummary(listOf(PingSample(10.0), PingSample(null), PingSample(20.0)))
        assertEquals(2, summary.received)
        assertEquals(33, summary.lossPercent)
        assertEquals(15.0, summary.averageMs!!, 0.001)
    }

    @Test fun noRepliesHaveNoAverage() {
        val summary = PingSummary(listOf(PingSample(null), PingSample(null)))
        assertEquals(100, summary.lossPercent)
        assertNull(summary.averageMs)
    }
}
