package app.ownbridge

import app.ownbridge.core.AddressFamily
import app.ownbridge.core.ShadowsocksExport

internal data class SubscriptionRequest(
    val runId: String,
    val sshHost: String,
    val publicHost: String,
    val yaml: String,
)

internal fun CompositeDeploymentState.Settled.subscriptionRequest(): SubscriptionRequest? =
    completedExport()?.let { export ->
        SubscriptionRequest(
            record.runId,
            record.plan.sshHost,
            record.plan.addresses.firstOrNull { it.family == AddressFamily.IPV4 }?.value
                ?: record.plan.sshHost,
            export.clashYaml,
        )
    }

internal fun DeploymentState.ServerReady.subscriptionRequest(): SubscriptionRequest =
    SubscriptionRequest(runId, plan.sshHost, plan.address,
        ShadowsocksExport(plan, secret).clashYaml)
