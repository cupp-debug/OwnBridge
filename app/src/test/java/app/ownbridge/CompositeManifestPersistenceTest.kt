package app.ownbridge

import app.ownbridge.core.AddressFamily
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositePlanManifest
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompositeManifestPersistenceTest {
    private val plan = CompositeDeploymentPlan("203.0.113.10", "Ubuntu 26.04.1 LTS", listOf(
        PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4),
            ProxyProtocol.SHADOWSOCKS, 28444),
    ))

    @Test fun singleLineStorageRoundTripsThroughStrictPlanParser() {
        val stored = CompositeManifestPersistence.encode(CompositePlanManifest.bytes(plan))
        assertFalse(stored.contains('\n'))
        assertEquals(plan, CompositePlanManifest.parse(
            plan.sshHost, plan.os, CompositeManifestPersistence.decode(stored),
        ))
    }

    @Test fun legacyXmlIndentationAfterFinalNewlineCanBeMigrated() {
        val old = CompositePlanManifest.bytes(plan).toString(Charsets.US_ASCII) + "    "
        assertEquals(plan, CompositePlanManifest.parse(
            plan.sshHost, plan.os, CompositeManifestPersistence.decodeLegacy(old),
        ))
    }
}
