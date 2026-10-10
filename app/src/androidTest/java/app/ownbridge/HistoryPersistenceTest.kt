package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.HistoryDelivery
import app.ownbridge.core.ShadowsocksPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryPersistenceTest {
    @Test fun completedSnapshotUpdatesAndDeletesWithoutStoringRootPassword() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val history = HistoryController(context, scope)
        val id = UUID.randomUUID().toString().replace("-", "")
        val file = File(context.filesDir, "history-v1/$id.obh")
        try {
            withTimeout(5_000) { while (history.loading) delay(20) }
            assertFalse(history.saveCompleted(id))
            val plan = ShadowsocksPlan("203.0.113.9", "203.0.113.9", 28443, "Ubuntu 24.04")
            history.captureLegacy(DeploymentState.Failed(id, plan, emptyList(),
                DeploymentFailure.REMOTE_STEP), "root-password")
            assertFalse(history.saveCompleted(id))
            val state = DeploymentState.ServerReady(id, plan, emptyList(), "proxy-secret")
            history.captureLegacy(state, "root-password")
            history.captureDelivery(id, plan.sshHost, "proxies: []",
                SubscriptionDeliveryState.Publishing(1))
            history.captureDelivery(id, plan.sshHost, "proxies: []",
                SubscriptionDeliveryState.Publishing(1))
            history.captureDelivery(id, plan.sshHost, "proxies: []",
                SubscriptionDeliveryState.Ready("http://203.0.113.9/link"))

            assertFalse(history.records.any { it.id == id })
            assertFalse(file.exists())
            assertTrue(history.saveCompleted(id))
            val saved = history.records.first { it.id == id }
            assertTrue(saved.subscriptionUrl == "http://203.0.113.9/link")
            assertTrue(saved.deliveryAttempts == 1)
            assertTrue(saved.deliveryEvents.map { it.state } == listOf(
                HistoryDelivery.PUBLISHING, HistoryDelivery.READY))
            assertTrue(file.exists())
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("proxy-secret"))
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("root-password"))
            history.captureDelivery(id, plan.sshHost, "proxies: []",
                SubscriptionDeliveryState.Ready("http://203.0.113.9/new-link"))
            withTimeout(5_000) {
                while (history.records.first { it.id == id }.subscriptionUrl !=
                    "http://203.0.113.9/new-link") delay(20)
            }
            assertTrue(history.deleteRecord(id))
            assertFalse(file.exists())
            assertFalse(history.records.any { it.id == id })
        } finally {
            scope.cancel()
            file.delete()
            File(file.path + ".new").delete()
            File(file.path + ".bak").delete()
        }
    }
}
