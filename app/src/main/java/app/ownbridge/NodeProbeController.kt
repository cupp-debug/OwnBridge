package app.ownbridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ownbridge.core.CompositeClashExport
import app.ownbridge.core.AddressFamily
import app.ownbridge.core.NamedDeployedNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class NodeProbePhase { IDLE, RUNNING, FINISHED, VPN_ACTIVE, ENGINE_FAILED }
internal enum class NodeProbeStatus { WAITING, TESTING, REACHABLE, UNREACHABLE, IPV6_UNAVAILABLE }

internal data class NodeProbeItem(
    val node: NamedDeployedNode,
    val status: NodeProbeStatus = NodeProbeStatus.WAITING,
    val delayMs: Int? = null,
)

internal data class NodeProbeState(
    val phase: NodeProbePhase = NodeProbePhase.IDLE,
    val items: List<NodeProbeItem> = emptyList(),
)

private class VpnRouteChanged : IllegalStateException()

internal fun prepareNodeProbeItems(
    nodes: List<NamedDeployedNode>,
    ipv6: Ipv6NetworkAvailability,
): List<NodeProbeItem> = nodes.map { node ->
    val unavailable = node.deployed.node.address.family == AddressFamily.IPV6 &&
        ipv6 == Ipv6NetworkAvailability.UNAVAILABLE
    NodeProbeItem(node, if (unavailable) NodeProbeStatus.IPV6_UNAVAILABLE else NodeProbeStatus.WAITING)
}

/** Owns only transient, non-secret measurements; a new run invalidates old results. */
internal class NodeProbeController(
    private val scope: CoroutineScope,
    private val probe: MihomoNodeProbe,
    private val networkRoute: SshNetworkRouteInspector,
    private val ipv6Network: Ipv6NetworkInspector,
) {
    var state by mutableStateOf(NodeProbeState())
        private set
    private var runId: String? = null
    private var job: Job? = null

    fun selectRun(id: String?) {
        if (runId == id) return
        job?.cancel()
        job = null
        runId = id
        state = NodeProbeState()
    }

    fun start(id: String, export: CompositeClashExport) {
        selectRun(id)
        if (state.phase == NodeProbePhase.RUNNING) return
        val pending = export.namedNodes.map(::NodeProbeItem)
        if (networkRoute.current() == SshNetworkRoute.VPN) {
            state = NodeProbeState(NodeProbePhase.VPN_ACTIVE, pending)
            return
        }
        val prepared = prepareNodeProbeItems(export.namedNodes, ipv6Network.current())
        val testNodes = prepared.filter { it.status == NodeProbeStatus.WAITING }.map(NodeProbeItem::node)
        if (testNodes.isEmpty()) {
            state = NodeProbeState(NodeProbePhase.FINISHED, prepared)
            return
        }
        state = NodeProbeState(NodeProbePhase.RUNNING, prepared)
        job = scope.launch {
            try {
                probe.test(export, testNodes,
                    onStarting = { node ->
                        if (networkRoute.current() == SshNetworkRoute.VPN) throw VpnRouteChanged()
                        update(node, NodeProbeStatus.TESTING, null)
                    },
                    onResult = { node, delay ->
                        if (networkRoute.current() == SshNetworkRoute.VPN) throw VpnRouteChanged()
                        update(node,
                            if (delay == null) NodeProbeStatus.UNREACHABLE else NodeProbeStatus.REACHABLE,
                            delay)
                    },
                )
                state = state.copy(phase = NodeProbePhase.FINISHED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: VpnRouteChanged) {
                state = NodeProbeState(NodeProbePhase.VPN_ACTIVE, pending)
            } catch (_: Exception) {
                state = state.copy(phase = NodeProbePhase.ENGINE_FAILED)
            } finally {
                if (job == coroutineContext[Job]) job = null
            }
        }
    }

    fun dispose() {
        job?.cancel()
        job = null
    }

    private suspend fun update(node: NamedDeployedNode, status: NodeProbeStatus, delay: Int?) {
        withContext(Dispatchers.Main) {
            state = state.copy(items = state.items.map { item ->
                if (item.node == node) item.copy(status = status, delayMs = delay) else item
            })
        }
    }
}
