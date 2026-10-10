package app.ownbridge

import app.ownbridge.core.AddressFamily
import app.ownbridge.core.CompositeDeploymentJournal
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositeProgress
import app.ownbridge.core.CompositeStep
import app.ownbridge.core.CompositeWorkOrder
import app.ownbridge.core.DeployedNode
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProtocolStage
import app.ownbridge.core.ProxyCredential
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.ShadowsocksPlan
import app.ownbridge.core.StepState
import app.ownbridge.core.TargetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressActionPolicyTest {
    private val address = TargetAddress("203.0.113.10", AddressFamily.IPV4)
    private val ss = PlannedNode(address, ProxyProtocol.SHADOWSOCKS, 28443)
    private val vless = PlannedNode(address, ProxyProtocol.VLESS, 28444)
    private val plan = CompositeDeploymentPlan("203.0.113.10", "Ubuntu 26.04", listOf(ss, vless))
    private val record = CompositeDeploymentRecord("a".repeat(32), plan,
        CompositeDeploymentPackageCatalog.VERSION)
    private val ssExport = DeployedNode(ss, ProxyCredential.Shadowsocks("a".repeat(32)))

    private fun result(
        states: Map<CompositeStep, StepState>,
        nodes: List<DeployedNode>,
        failure: CompositeRunFailure? = null,
    ) = CompositeDeploymentState.Settled(record,
        CompositeRunResult(CompositeDeploymentJournal(states), nodes, emptyList(), failure))

    private fun allDone() = CompositeWorkOrder.forPlan(plan).associateWith { StepState.DONE }

    private fun action(state: CompositeDeploymentState, credentials: Boolean = true) =
        ProgressActionPolicy.composite(state, credentials, hostKeyTrusted = true)

    @Test fun onlyCompleteJournalAndCompleteExportCanAdvance() {
        val completed = allDone()
        val partial = completed + (CompositeStep.Protocol(ProxyProtocol.VLESS, ProtocolStage.VERIFY) to
            StepState.PENDING)
        assertEquals(ProgressAction.RETRY, action(result(partial, listOf(ssExport))))
        assertEquals(ProgressAction.RETRY, action(result(completed, listOf(ssExport))))
        assertEquals(CompositeProgress.ALL_SERVER_READY,
            CompositeDeploymentJournal(completed).overall(plan))
        val vlessExport = DeployedNode(vless, ProxyCredential.VlessReality(
            "00000000-0000-0000-0000-000000000001", "A".repeat(43), "a".repeat(16),
            "example.com"))
        assertEquals(ProgressAction.NEXT, action(result(completed, listOf(ssExport, vlessExport))))
    }

    @Test fun savedTaskRetriesWithoutChangingPlanWhenCredentialsRemain() {
        assertEquals(ProgressAction.RETRY,
            action(CompositeDeploymentState.NeedsReconciliation(record)))
        assertEquals(ProgressAction.ENTER_CREDENTIALS,
            action(CompositeDeploymentState.NeedsReconciliation(record), credentials = false))
        assertEquals(ProgressAction.RUNNING,
            action(CompositeDeploymentState.Running(record, emptyList())))
        assertEquals(ProgressAction.HIDDEN, action(CompositeDeploymentState.Idle))
    }

    @Test fun identityAndAuthenticationFailuresRequireConnectionInput() {
        val states = allDone()
        assertEquals(ProgressAction.CHECK_CONNECTION,
            action(result(states, emptyList(), CompositeRunFailure.IDENTITY)))
        assertEquals(ProgressAction.ENTER_CREDENTIALS,
            action(result(states, emptyList(), CompositeRunFailure.AUTHENTICATION)))
        assertEquals(ProgressAction.REVIEW_PLAN,
            action(result(states, emptyList(), CompositeRunFailure.HASH_MISMATCH)))
        assertEquals(ProgressAction.RETRY,
            action(result(states, emptyList(), CompositeRunFailure.INTERRUPTED)))
    }

    @Test fun legacyPathAlsoNeedsExplicitNextAndRetry() {
        val oldPlan = ShadowsocksPlan("203.0.113.10", address.value, 28443, "Ubuntu 26.04")
        assertEquals(ProgressAction.NEXT, ProgressActionPolicy.legacy(
            DeploymentState.ServerReady("b".repeat(32), oldPlan, emptyList(), "secret"),
            credentialsAvailable = false, hostKeyTrusted = true))
        assertEquals(ProgressAction.RETRY, ProgressActionPolicy.legacy(
            DeploymentState.Failed("b".repeat(32), oldPlan, emptyList(), DeploymentFailure.NETWORK),
            credentialsAvailable = true, hostKeyTrusted = true))
    }
}
