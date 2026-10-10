package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HistorySnapshotCodecTest {
    private val snapshot = HistorySnapshot(
        id = "detect-abcdef1234567890abcdef1234567890",
        kind = HistoryKind.CONNECTION,
        host = "203.0.113.7",
        startedAt = 100,
        updatedAt = 200,
        phase = HistoryPhase.CONNECTION,
        outcome = HistoryOutcome.SUCCEEDED,
        addresses = listOf("203.0.113.7", "2001:db8::7"),
        logs = listOf(HistoryLog("SYSTEM", "PASSED", "uname -a", result = "Ubuntu")),
        clashYaml = "proxies: []\n",
        subscriptionUrl = "http://203.0.113.7:28600/s/secret.yaml",
        delivery = HistoryDelivery.READY,
        deliveryAttempts = 2,
        deliveryEvents = listOf(
            HistoryDeliveryEvent(150, HistoryDelivery.PUBLISHING),
            HistoryDeliveryEvent(160, HistoryDelivery.FAILED, "RETRIEVAL"),
            HistoryDeliveryEvent(190, HistoryDelivery.READY),
        ),
    )

    @Test fun roundTripPreservesEveryField() {
        assertEquals(snapshot, HistorySnapshotCodec.decode(HistorySnapshotCodec.encode(snapshot)))
    }

    @Test fun interruptedStateDoesNotClaimSuccess() {
        assertEquals(HistoryOutcome.INTERRUPTED,
            snapshot.copy(outcome = HistoryOutcome.RUNNING).interruptedIfRunning().outcome)
        assertEquals(snapshot, snapshot.interruptedIfRunning())
    }

    @Test fun interruptedDeliveryRetainsAnAlreadyDeliveredLink() {
        val recovered = snapshot.copy(delivery = HistoryDelivery.PUBLISHING).interruptedIfRunning()
        assertEquals(HistoryDelivery.READY, recovered.delivery)
        assertEquals("INTERRUPTED", recovered.deliveryFailure)
        assertEquals(HistoryDelivery.FAILED, recovered.deliveryEvents.last().state)
    }

    @Test fun malformedAndOversizedSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { HistorySnapshotCodec.decode(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            HistorySnapshotCodec.decode(ByteArray(HistorySnapshotCodec.MAX_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistorySnapshotCodec.decode(HistorySnapshotCodec.encode(snapshot) + 0.toByte())
        }
    }
}
