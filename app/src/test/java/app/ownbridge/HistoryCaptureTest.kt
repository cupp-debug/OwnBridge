package app.ownbridge

import app.ownbridge.core.AddressFamily
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.HistoryOutcome
import app.ownbridge.core.HistoryDelivery
import app.ownbridge.core.HistoryKind
import app.ownbridge.core.HistoryPhase
import app.ownbridge.core.HistorySnapshot
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.ShadowsocksPlan
import app.ownbridge.core.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCaptureTest {
    @Test fun failedDeploymentRetainsAttemptLogWithoutInventingExport() {
        val plan = CompositeDeploymentPlan("203.0.113.7", "Ubuntu 24.04", listOf(
            PlannedNode(TargetAddress("203.0.113.7", AddressFamily.IPV4),
                ProxyProtocol.SHADOWSOCKS, 28443)))
        val record = CompositeDeploymentRecord("a".repeat(32), plan, "test-bundle")
        val event = CompositeRunEvent(CompositeRunAction.STEP, null, "run", "script",
            CompositeRunEventStatus.FAILED, "password=private-pass")
        val snapshot = HistoryCapture.composite(CompositeDeploymentState.Settled(record,
            CompositeRunResult(null, emptyList(), listOf(event), CompositeRunFailure.REMOTE)),
            "private-pass", null, 100)!!
        assertEquals(HistoryOutcome.FAILED, snapshot.outcome)
        assertEquals("composite:REMOTE", snapshot.reason)
        assertEquals(null, snapshot.clashYaml)
        assertFalse(snapshot.logs.single().result!!.contains("private-pass"))
        assertTrue(snapshot.logs.single().source!!.contains("script"))
    }

    @Test fun earlierLegacyFailureStaysAFailure() {
        val plan = ShadowsocksPlan("203.0.113.7", "203.0.113.7", 28443, "Ubuntu 24.04")
        val snapshot = HistoryCapture.legacy(DeploymentState.Failed("b".repeat(32), plan,
            emptyList(), DeploymentFailure.AUTHENTICATION), "pass", null, 100)!!
        assertEquals(HistoryOutcome.FAILED, snapshot.outcome)
        assertEquals("legacy:AUTHENTICATION", snapshot.reason)
    }

    @Test fun failedLinkRetryThenSuccessKeepsBothAttempts() {
        val initial = HistorySnapshot("c".repeat(32), HistoryKind.DEPLOYMENT,
            "203.0.113.7", 100, 100, HistoryPhase.DEPLOYMENT,
            HistoryOutcome.SUCCEEDED)
        val publishing = HistoryCapture.delivery(initial, "yaml",
            SubscriptionDeliveryState.Publishing(1), 101)
        val failed = HistoryCapture.delivery(publishing, "yaml",
            SubscriptionDeliveryState.Failed(SubscriptionFailure.REMOTE), 102)
        val retry = HistoryCapture.delivery(failed, "yaml",
            SubscriptionDeliveryState.Publishing(1), 103)
        val ready = HistoryCapture.delivery(retry, "yaml",
            SubscriptionDeliveryState.Ready("http://203.0.113.7/link"), 104)
        assertEquals(HistoryOutcome.SUCCEEDED, ready.outcome)
        assertEquals(HistoryDelivery.READY, ready.delivery)
        assertEquals(2, ready.deliveryAttempts)
        assertEquals(listOf(HistoryDelivery.PUBLISHING, HistoryDelivery.FAILED,
            HistoryDelivery.PUBLISHING, HistoryDelivery.READY),
            ready.deliveryEvents.map { it.state })
        assertEquals("http://203.0.113.7/link", ready.subscriptionUrl)

        val laterFailure = HistoryCapture.delivery(ready, "yaml",
            SubscriptionDeliveryState.Failed(SubscriptionFailure.CONNECTION), 105)
        assertEquals(HistoryDelivery.READY, laterFailure.delivery)
        assertEquals(HistoryDelivery.FAILED, laterFailure.deliveryEvents.last().state)
        assertEquals(ready.subscriptionUrl, laterFailure.subscriptionUrl)
    }

    @Test fun transientFailureIsRecordedWhileButtonKeepsPublishing() {
        val initial = HistorySnapshot("d".repeat(32), HistoryKind.DEPLOYMENT,
            "203.0.113.7", 100, 100, HistoryPhase.DEPLOYMENT,
            HistoryOutcome.SUCCEEDED)
        val first = HistoryCapture.delivery(initial, "yaml",
            SubscriptionDeliveryState.Publishing(1), 101)
        val waiting = HistoryCapture.delivery(first, "yaml",
            SubscriptionDeliveryState.Publishing(1, SubscriptionFailure.REMOTE), 102)
        val second = HistoryCapture.delivery(waiting, "yaml",
            SubscriptionDeliveryState.Publishing(2), 103)
        val ready = HistoryCapture.delivery(second, "yaml",
            SubscriptionDeliveryState.Ready("http://203.0.113.7/link"), 104)
        assertEquals(HistoryDelivery.READY, ready.delivery)
        assertEquals(2, ready.deliveryAttempts)
        assertEquals(listOf(HistoryDelivery.PUBLISHING, HistoryDelivery.FAILED,
            HistoryDelivery.PUBLISHING, HistoryDelivery.READY),
            ready.deliveryEvents.map { it.state })
        assertEquals("REMOTE", ready.deliveryEvents[1].failure)
    }
}
