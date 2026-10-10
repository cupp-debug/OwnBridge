package app.ownbridge

import app.ownbridge.core.CompositeProgress
import app.ownbridge.core.HistoryDelivery
import app.ownbridge.core.HistoryDeliveryEvent
import app.ownbridge.core.HistoryKind
import app.ownbridge.core.HistoryLog
import app.ownbridge.core.HistoryOutcome
import app.ownbridge.core.HistoryPhase
import app.ownbridge.core.HistorySnapshot
import app.ownbridge.core.ProxyCredential
import app.ownbridge.core.ShadowsocksExport
import app.ownbridge.core.CompositeV2RayExport

/** Converts observable steps into bounded snapshots without retaining the root password. */
internal object HistoryCapture {
    fun composite(
        state: CompositeDeploymentState, password: String,
        previous: HistorySnapshot?, now: Long,
    ): HistorySnapshot? {
        if (state is CompositeDeploymentState.NeedsReconciliation) {
            return previous?.takeIf { it.outcome == HistoryOutcome.RUNNING }
                ?.copy(outcome = HistoryOutcome.INTERRUPTED, updatedAt = now)
        }
        val record = when (state) {
            is CompositeDeploymentState.NeedsReconciliation -> state.record
            is CompositeDeploymentState.Running -> state.record
            is CompositeDeploymentState.Settled -> state.record
            else -> return null
        }
        val events = when (state) {
            is CompositeDeploymentState.Running -> state.events
            is CompositeDeploymentState.Settled -> state.result.events
            else -> emptyList()
        }
        val complete = state is CompositeDeploymentState.Settled && state.completedExport() != null
        val outcome = when (state) {
            is CompositeDeploymentState.Running -> HistoryOutcome.RUNNING
            is CompositeDeploymentState.Settled -> when {
                complete -> HistoryOutcome.SUCCEEDED
                state.result.failure != null -> HistoryOutcome.FAILED
                state.result.journal?.overall(record.plan) == CompositeProgress.PENDING ->
                    HistoryOutcome.INTERRUPTED
                else -> HistoryOutcome.FAILED
            }
            is CompositeDeploymentState.NeedsReconciliation -> error("Handled above")
        }
        val secrets = (state as? CompositeDeploymentState.Settled)?.result?.nodes.orEmpty()
            .mapNotNull { node -> when (val credential = node.credential) {
                is ProxyCredential.Shadowsocks -> credential.password
                is ProxyCredential.Hysteria2 -> credential.password
                is ProxyCredential.VlessReality -> credential.uuid
            } }
        val logs = if (events.isEmpty()) previous?.logs.orEmpty() else bounded(events.map { event ->
            HistoryLog(event.step?.wireName ?: event.action.name, event.status.name,
                event.command?.safe(password, secrets), event.source?.safe(password, secrets),
                event.detail?.safe(password, secrets, redactAssignments = true))
        })
        return HistorySnapshot(
            id = record.runId,
            kind = HistoryKind.DEPLOYMENT,
            host = record.plan.sshHost,
            startedAt = previous?.startedAt ?: now,
            updatedAt = now,
            phase = previous?.phase?.takeIf { complete && it == HistoryPhase.DELIVERY }
                ?: HistoryPhase.DEPLOYMENT,
            outcome = outcome,
            step = events.lastOrNull()?.step?.wireName ?: events.lastOrNull()?.action?.name ?: previous?.step,
            reason = (state as? CompositeDeploymentState.Settled)?.result?.failure
                ?.let { "composite:${it.name}" } ?: previous?.reason?.takeIf { outcome == HistoryOutcome.INTERRUPTED },
            system = record.plan.os,
            fingerprint = previous?.fingerprint,
            addresses = record.plan.addresses.map { it.value },
            protocols = record.plan.protocols.map { it.name },
            logs = logs,
            clashYaml = if (complete) state.completedExport()?.clashYaml else previous?.clashYaml,
            nodeUri = if (complete) CompositeV2RayExport(state.result.nodes).shareLinks
                else previous?.nodeUri,
            subscriptionUrl = previous?.subscriptionUrl,
            delivery = previous?.delivery ?: app.ownbridge.core.HistoryDelivery.NONE,
            deliveryFailure = previous?.deliveryFailure,
            deliveryAttempts = previous?.deliveryAttempts ?: 0,
            deliveryEvents = previous?.deliveryEvents.orEmpty(),
            archived = previous?.archived ?: false,
        )
    }

    fun legacy(state: DeploymentState, password: String,
        previous: HistorySnapshot?, now: Long): HistorySnapshot? {
        val (runId, plan, events) = when (state) {
            is DeploymentState.Running -> Triple(state.runId, state.plan, state.events)
            is DeploymentState.Failed -> Triple(state.runId, state.plan, state.events)
            is DeploymentState.ServerReady -> Triple(state.runId, state.plan, state.events)
            DeploymentState.Idle -> return null
        }
        val ready = state as? DeploymentState.ServerReady
        val export = ready?.let { ShadowsocksExport(it.plan, it.secret) }
        return HistorySnapshot(
            id = runId,
            kind = HistoryKind.DEPLOYMENT,
            host = plan.sshHost,
            startedAt = previous?.startedAt ?: now,
            updatedAt = now,
            phase = previous?.phase?.takeIf { ready != null && it == HistoryPhase.DELIVERY }
                ?: HistoryPhase.DEPLOYMENT,
            outcome = when (state) {
                is DeploymentState.Running -> HistoryOutcome.RUNNING
                is DeploymentState.Failed -> HistoryOutcome.FAILED
                is DeploymentState.ServerReady -> HistoryOutcome.SUCCEEDED
                DeploymentState.Idle -> error("Unreachable")
            },
            step = events.lastOrNull()?.step?.name ?: events.lastOrNull()?.action?.name,
            reason = (state as? DeploymentState.Failed)?.reason?.let { "legacy:${it.name}" },
            system = plan.os,
            addresses = listOf(plan.address),
            protocols = listOf("SHADOWSOCKS"),
            logs = bounded(events.map { event ->
                HistoryLog(event.step?.name ?: event.action.name, event.status.name,
                    event.command?.safe(password, listOfNotNull(ready?.secret)),
                    event.source?.safe(password, listOfNotNull(ready?.secret)),
                    event.result?.safe(password, listOfNotNull(ready?.secret),
                        redactAssignments = true))
            }),
            clashYaml = export?.clashYaml ?: previous?.clashYaml,
            nodeUri = export?.nodeUri ?: previous?.nodeUri,
            subscriptionUrl = previous?.subscriptionUrl,
            delivery = previous?.delivery ?: app.ownbridge.core.HistoryDelivery.NONE,
            deliveryFailure = previous?.deliveryFailure,
            deliveryAttempts = previous?.deliveryAttempts ?: 0,
            deliveryEvents = previous?.deliveryEvents.orEmpty(),
            archived = previous?.archived ?: false,
        )
    }

    fun delivery(previous: HistorySnapshot, yaml: String,
        state: SubscriptionDeliveryState, now: Long): HistorySnapshot {
        require(state != SubscriptionDeliveryState.Idle)
        val observed = when (state) {
            is SubscriptionDeliveryState.Publishing -> HistoryDelivery.PUBLISHING
            is SubscriptionDeliveryState.Ready -> HistoryDelivery.READY
            is SubscriptionDeliveryState.Failed -> HistoryDelivery.FAILED
            SubscriptionDeliveryState.Idle -> error("Handled above")
        }
        val retryFailure = (state as? SubscriptionDeliveryState.Publishing)
            ?.retryReason?.name
        val failure = (state as? SubscriptionDeliveryState.Failed)?.reason?.name
            ?: retryFailure
        val eventState = if (retryFailure != null) HistoryDelivery.FAILED else observed
        return previous.copy(
            updatedAt = now,
            phase = HistoryPhase.DELIVERY,
            delivery = if (observed == HistoryDelivery.FAILED &&
                previous.subscriptionUrl != null) HistoryDelivery.READY else observed,
            deliveryFailure = failure,
            deliveryAttempts = if (state is SubscriptionDeliveryState.Publishing &&
                state.retryReason == null) previous.deliveryAttempts + 1
                else previous.deliveryAttempts,
            deliveryEvents = (previous.deliveryEvents + HistoryDeliveryEvent(
                now, eventState, failure)).takeLast(1_000),
            subscriptionUrl = (state as? SubscriptionDeliveryState.Ready)?.url
                ?: previous.subscriptionUrl,
            clashYaml = previous.clashYaml ?: yaml,
        )
    }

    private fun String.safe(password: String, otherSecrets: List<String> = emptyList(),
        redactAssignments: Boolean = false): String {
        var value = this
        (listOf(password) + otherSecrets).filter { it.isNotBlank() }.forEach { secret ->
            value = value.replace(secret, "[redacted]")
        }
        if (redactAssignments) value = value.replace(
            Regex("(?im)(password|private[_ -]?key|token|secret)\\s*[:=]\\s*[^\\s]+"),
            "$1=[redacted]",
        )
        return value.filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }.take(32_000)
    }

    private fun bounded(logs: List<HistoryLog>): List<HistoryLog> {
        val retained = mutableListOf<HistoryLog>()
        var bytes = 0
        for (log in logs.asReversed()) {
            val length = listOfNotNull(log.action, log.status, log.command, log.source, log.result)
                .sumOf { it.toByteArray(Charsets.UTF_8).size }
            if (retained.size >= 255 || bytes + length > 1_000_000) break
            retained += log
            bytes += length
        }
        retained.reverse()
        return if (retained.size == logs.size) retained else
            listOf(HistoryLog("EARLIER_EVENTS", "TRUNCATED")) + retained
    }
}
