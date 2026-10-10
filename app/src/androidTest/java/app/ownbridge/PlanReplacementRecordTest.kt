package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.AddressFamily
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.ShadowsocksPlan
import app.ownbridge.core.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlanReplacementRecordTest {
    @Test fun compositeReplacementArchivesOldPointerAndActivatesNewPlan() {
        val store = CompositeDeploymentRecordStore(InstrumentationRegistry.getInstrumentation().context)
        store.clear()
        val previous = CompositeDeploymentRecord(id(), compositePlan(28443),
            CompositeDeploymentPackageCatalog.VERSION)
        val next = CompositeDeploymentRecord(id(), compositePlan(28444),
            CompositeDeploymentPackageCatalog.VERSION)
        try {
            assertTrue(store.save(previous))
            assertFalse(store.save(next))
            assertFalse(store.replaceActive(previous.copy(runId = id()), next))
            assertEquals(CompositeStoredRecord.Valid(previous), store.loadState())
            assertTrue(store.replaceActive(previous, next))
            assertEquals(CompositeStoredRecord.Valid(next), store.loadState())
            assertTrue(previous in store.archived())
            assertFalse(store.replaceActive(previous, next))
        } finally {
            store.clear()
        }
    }

    @Test fun legacyReplacementIsConditionalOnTheCurrentPointer() {
        val store = DeploymentRecordStore(InstrumentationRegistry.getInstrumentation().context)
        val existing = store.load()
        existing?.let(store::release)
        val previous = DeploymentRecord(id(), ShadowsocksPlan("203.0.113.9", "203.0.113.9",
            28443, "Ubuntu 24.04"), DeploymentPackageCatalog.CURRENT_VERSION)
        val next = previous.copy(runId = id(), plan = previous.plan.copy(port = 28444))
        try {
            assertTrue(store.save(previous))
            assertFalse(store.replaceActive(previous.copy(runId = id()), next))
            assertEquals(previous, store.load())
            assertTrue(store.replaceActive(previous, next))
            assertEquals(next, store.load())
            assertFalse(store.replaceActive(previous, next))
        } finally {
            store.release(next)
        }
    }

    private fun compositePlan(port: Int) = CompositeDeploymentPlan(
        "203.0.113.9", "Ubuntu 24.04", listOf(
            PlannedNode(TargetAddress("203.0.113.9", AddressFamily.IPV4),
                ProxyProtocol.SHADOWSOCKS, port),
        ),
    )

    private fun id(): String = UUID.randomUUID().toString().replace("-", "")
}
