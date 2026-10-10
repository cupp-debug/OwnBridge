package app.ownbridge

import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.ShadowsocksPlan

internal sealed interface PlanExecution {
    data class Legacy(val plan: ShadowsocksPlan) : PlanExecution
    data class Composite(val plan: CompositeDeploymentPlan) : PlanExecution
}

internal fun PlanSelection.compositeProtocols(): List<ProxyProtocol> = protocols.map { name ->
    when (name) {
        "SS" -> ProxyProtocol.SHADOWSOCKS
        "VLESS" -> ProxyProtocol.VLESS
        "HY2" -> ProxyProtocol.HYSTERIA2
        else -> error("Unknown protocol choice")
    }
}
