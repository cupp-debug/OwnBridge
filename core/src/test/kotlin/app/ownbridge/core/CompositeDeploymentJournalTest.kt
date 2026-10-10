package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeDeploymentJournalTest {
    private val plan = CompositeDeploymentPlan("203.0.113.10", "Ubuntu 26.04", listOf(
        PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4), ProxyProtocol.SHADOWSOCKS, 28443),
        PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4), ProxyProtocol.HYSTERIA2, 28444),
    ))
    private val steps = CompositeWorkOrder.forPlan(plan)

    @Test fun includesSharedWorkOnceAndOnlySelectedModules() {
        assertEquals(listOf(
            "shared.preflight", "shared.fetch", "ss.configure", "ss.activate", "ss.verify",
            "hy2.configure", "hy2.activate", "hy2.verify",
        ), steps.map(CompositeStep::wireName))
    }

    @Test fun parsesStrictlyAndShowsPartialServerReadiness() {
        val states = mapOf(
            "shared.preflight" to "done", "shared.fetch" to "done",
            "ss.configure" to "done", "ss.activate" to "done", "ss.verify" to "done",
            "hy2.configure" to "failed", "hy2.activate" to "pending", "hy2.verify" to "pending",
        )
        val journal = CompositeJournalParser.parse(render(states), plan)
        assertEquals(ProtocolProgress.SERVER_READY,
            journal.protocolProgress(ProxyProtocol.SHADOWSOCKS))
        assertEquals(ProtocolProgress.FAILED,
            journal.protocolProgress(ProxyProtocol.HYSTERIA2))
        assertEquals(CompositeProgress.PARTIAL, journal.overall(plan))
    }

    @Test fun sharedFailureIsNotPartialSuccessAndUncertainIsNotSuccess() {
        val done = steps.associate { it.wireName to "done" }
        assertEquals(CompositeProgress.FAILED,
            CompositeJournalParser.parse(render(done + ("shared.fetch" to "failed")), plan).overall(plan))
        assertEquals(CompositeProgress.UNCERTAIN,
            CompositeJournalParser.parse(render(done + ("hy2.verify" to "uncertain")), plan).overall(plan))
        assertEquals(CompositeProgress.PENDING,
            CompositeJournalParser.parse(render(done + ("shared.fetch" to "pending")), plan).overall(plan))
    }

    @Test fun rejectsMissingDuplicateOrUnknownJournalEntries() {
        val done = steps.associate { it.wireName to "done" }
        assertThrows(IllegalArgumentException::class.java) {
            CompositeJournalParser.parse(render(done).replace("hy2.verify=done\n", ""), plan)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompositeJournalParser.parse(render(done).replace("hy2.verify=done", "ss.verify=done"), plan)
        }
        assertThrows(IllegalStateException::class.java) {
            CompositeJournalParser.parse(render(done).replace("hy2.verify=done", "vless.verify=done"), plan)
        }
    }

    @Test fun retryDoesNotRestartHealthyProtocolButCanRepairFailedVerification() {
        val done = steps.associateWith { StepState.DONE }
        val healthy = CompositeDeploymentJournal(done)
        assertEquals(listOf(CompositeStep.Protocol(ProxyProtocol.SHADOWSOCKS, ProtocolStage.VERIFY)),
            CompositeRetryPolicy.protocolSteps(healthy, ProxyProtocol.SHADOWSOCKS))
        val failedVerify = CompositeDeploymentJournal(done + (
            CompositeStep.Protocol(ProxyProtocol.HYSTERIA2, ProtocolStage.VERIFY) to StepState.FAILED
        ))
        assertEquals(listOf(
            CompositeStep.Protocol(ProxyProtocol.HYSTERIA2, ProtocolStage.ACTIVATE),
            CompositeStep.Protocol(ProxyProtocol.HYSTERIA2, ProtocolStage.VERIFY),
        ), CompositeRetryPolicy.protocolSteps(failedVerify, ProxyProtocol.HYSTERIA2))
    }

    @Test fun interruptedModuleRetriesOnlyItsNecessarySuffix() {
        val done = steps.associateWith { StepState.DONE }
        val protocol = ProxyProtocol.HYSTERIA2
        val expected = mapOf(
            ProtocolStage.CONFIGURE to listOf(
                ProtocolStage.CONFIGURE, ProtocolStage.ACTIVATE, ProtocolStage.VERIFY,
            ),
            ProtocolStage.ACTIVATE to listOf(ProtocolStage.ACTIVATE, ProtocolStage.VERIFY),
            ProtocolStage.VERIFY to listOf(ProtocolStage.ACTIVATE, ProtocolStage.VERIFY),
        )
        expected.forEach { (interrupted, stages) ->
            val journal = CompositeDeploymentJournal(done + (
                CompositeStep.Protocol(protocol, interrupted) to StepState.UNCERTAIN
            ))
            assertEquals(stages.map { CompositeStep.Protocol(protocol, it) },
                CompositeRetryPolicy.protocolSteps(journal, protocol))
            assertEquals(listOf(CompositeStep.Protocol(ProxyProtocol.SHADOWSOCKS, ProtocolStage.VERIFY)),
                CompositeRetryPolicy.protocolSteps(journal, ProxyProtocol.SHADOWSOCKS))
        }
    }

    @Test fun failedSharedPreflightCanBeAbandonedOnlyBeforeProtocolWorkStarts() {
        val pending = steps.associateWith { StepState.PENDING } +
            (CompositeStep.Shared(SharedStage.PREFLIGHT) to StepState.FAILED)
        assertTrue(CompositeDeploymentJournal(pending).noProtocolWorkStarted(plan))
        val configuring = pending + (
            CompositeStep.Protocol(ProxyProtocol.SHADOWSOCKS, ProtocolStage.CONFIGURE) to StepState.UNCERTAIN
        )
        assertFalse(CompositeDeploymentJournal(configuring).noProtocolWorkStarted(plan))
        val activated = pending + (
            CompositeStep.Protocol(ProxyProtocol.HYSTERIA2, ProtocolStage.ACTIVATE) to StepState.DONE
        )
        assertFalse(CompositeDeploymentJournal(activated).noProtocolWorkStarted(plan))
    }

    private fun render(states: Map<String, String>): String =
        states.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }
}
