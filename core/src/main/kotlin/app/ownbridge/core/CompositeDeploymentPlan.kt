package app.ownbridge.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

enum class AddressFamily { IPV4, IPV6 }

data class TargetAddress(val value: String, val family: AddressFamily) {
    init {
        require(value.isNotBlank() && value == value.trim() && '%' !in value)
        val parsed = if (family == AddressFamily.IPV4 && isIpv4Literal(value)) {
            InetAddress.getByName(value)
        } else if (family == AddressFamily.IPV6 && ':' in value) {
            InetAddress.getByName(value)
        } else {
            null
        }
        require(
            (family == AddressFamily.IPV4 && parsed is Inet4Address) ||
                (family == AddressFamily.IPV6 && parsed is Inet6Address)
        )
    }
}

enum class ProxyProtocol(val wireName: String) {
    SHADOWSOCKS("ss"), VLESS("vless"), HYSTERIA2("hy2");

    val transport: Transport
        get() = when (this) {
            SHADOWSOCKS -> Transport.TCP_AND_UDP
            VLESS -> Transport.TCP
            HYSTERIA2 -> Transport.UDP
        }
}

enum class Transport { TCP, UDP, TCP_AND_UDP }

data class PlannedNode(
    val address: TargetAddress,
    val protocol: ProxyProtocol,
    val port: Int,
) {
    init { require(port in 28443..28542) }

    val transport: Transport get() = protocol.transport
}

data class CompositeDeploymentPlan(
    val sshHost: String,
    val os: String,
    val nodes: List<PlannedNode>,
) {
    init {
        require(sshHost.isNotBlank() && nodes.isNotEmpty())
        require(nodes.distinctBy { it.address to it.protocol }.size == nodes.size)
        require(nodes.distinctBy { it.port }.size == nodes.size)
        require(nodes.size == addresses.size * protocols.size) {
            "Every selected protocol must be planned for every selected address"
        }
    }

    val addresses: List<TargetAddress> get() = nodes.map(PlannedNode::address).distinct()
    val protocols: List<ProxyProtocol> get() = nodes.map(PlannedNode::protocol).distinct()
}

data class DeploymentCapabilities(
    val protocols: Set<ProxyProtocol>,
    val maxIpv4Addresses: Int = 1,
    val maxIpv6Addresses: Int = 0,
) {
    init { require(maxIpv4Addresses >= 0 && maxIpv6Addresses >= 0) }

    companion object {
        val ExistingSsOnly = DeploymentCapabilities(setOf(ProxyProtocol.SHADOWSOCKS))
    }
}

enum class CompositePlanBlocker {
    NO_INVENTORY,
    ROOT_REQUIRED,
    UNSUPPORTED_SYSTEM,
    EMPTY_SELECTION,
    UNSUPPORTED_CLIENT,
    UNSUPPORTED_PROTOCOL,
    IPV6_NOT_ENABLED,
    ADDRESS_LIMIT_NOT_VERIFIED,
    INVALID_ADDRESS,
    NO_AVAILABLE_PORT,
    PLAN_MISMATCH,
}

sealed interface CompositePlanResult {
    data class Ready(val plan: CompositeDeploymentPlan) : CompositePlanResult
    data class Blocked(val reason: CompositePlanBlocker) : CompositePlanResult
}

/** Builds a deterministic, non-secret plan. Capabilities are enabled only after real traffic tests. */
object CompositeDeploymentPlanner {
    private val candidatePorts = 28443..28542
    private val supportedClients = setOf("Clash / Mihomo", "v2rayN / v2rayNG")

    private fun clientsSupported(clients: List<String>): Boolean =
        clients.isNotEmpty() && clients.distinct().size == clients.size &&
            clients.all { it in supportedClients }

    fun create(
        sshHost: String,
        username: String,
        inventory: ServerInventory?,
        addresses: List<String>,
        protocols: List<ProxyProtocol>,
        clients: List<String>,
        capabilities: DeploymentCapabilities,
    ): CompositePlanResult {
        if (inventory == null) return CompositePlanResult.Blocked(CompositePlanBlocker.NO_INVENTORY)
        if (username.trim() != "root") return CompositePlanResult.Blocked(CompositePlanBlocker.ROOT_REQUIRED)
        if (!SupportedUbuntu.matches(inventory.system)) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.UNSUPPORTED_SYSTEM)
        }
        if (sshHost.isBlank() || addresses.isEmpty() || protocols.isEmpty() || clients.isEmpty()) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.EMPTY_SELECTION)
        }
        if (!clientsSupported(clients)) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.UNSUPPORTED_CLIENT)
        }
        if (protocols.any { it !in capabilities.protocols }) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.UNSUPPORTED_PROTOCOL)
        }
        // Existing services do not block the entire VPS. Their occupied ports are excluded
        // below; the remote preflight checks exact address/protocol ownership before writes.

        val targets = mutableListOf<TargetAddress>()
        for (address in addresses.distinct()) {
            val family = when {
                address in inventory.ipv4Addresses -> AddressFamily.IPV4
                address in inventory.ipv6Addresses -> AddressFamily.IPV6
                else -> return CompositePlanResult.Blocked(CompositePlanBlocker.INVALID_ADDRESS)
            }
            if (family == AddressFamily.IPV6 && capabilities.maxIpv6Addresses == 0) {
                return CompositePlanResult.Blocked(CompositePlanBlocker.IPV6_NOT_ENABLED)
            }
            val target = runCatching { TargetAddress(address, family) }.getOrNull()
                ?: return CompositePlanResult.Blocked(CompositePlanBlocker.INVALID_ADDRESS)
            targets += target
        }
        if (targets.count { it.family == AddressFamily.IPV4 } > capabilities.maxIpv4Addresses ||
            targets.count { it.family == AddressFamily.IPV6 } > capabilities.maxIpv6Addresses) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED)
        }

        // Reserve unique ports conservatively across both IP families and transports. Wildcard
        // listeners and existing v1 services must not be accidentally reused by a new plan.
        val unavailable = inventory.listeningPorts.mapTo(mutableSetOf()) { it.port }
        val nodes = mutableListOf<PlannedNode>()
        for (target in targets) {
            for (protocol in ProxyProtocol.entries.filter { it in protocols }) {
                val port = candidatePorts.firstOrNull { it !in unavailable }
                    ?: return CompositePlanResult.Blocked(CompositePlanBlocker.NO_AVAILABLE_PORT)
                unavailable += port
                nodes += PlannedNode(target, protocol, port)
            }
        }
        return CompositePlanResult.Ready(CompositeDeploymentPlan(sshHost.trim(), inventory.system, nodes))
    }

    /** Keeps previously reserved ports. The remote runner must still verify run ownership. */
    fun resume(
        sshHost: String,
        username: String,
        inventory: ServerInventory?,
        addresses: List<String>,
        protocols: List<ProxyProtocol>,
        clients: List<String>,
        capabilities: DeploymentCapabilities,
        pending: CompositeDeploymentPlan,
    ): CompositePlanResult {
        if (inventory == null) return CompositePlanResult.Blocked(CompositePlanBlocker.NO_INVENTORY)
        if (username.trim() != "root") return CompositePlanResult.Blocked(CompositePlanBlocker.ROOT_REQUIRED)
        if (pending.protocols.any { it !in capabilities.protocols }) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.UNSUPPORTED_PROTOCOL)
        }
        if (pending.addresses.any { it.family == AddressFamily.IPV6 } &&
            capabilities.maxIpv6Addresses == 0) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.IPV6_NOT_ENABLED)
        }
        if (pending.addresses.count { it.family == AddressFamily.IPV4 } > capabilities.maxIpv4Addresses ||
            pending.addresses.count { it.family == AddressFamily.IPV6 } > capabilities.maxIpv6Addresses) {
            return CompositePlanResult.Blocked(CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED)
        }
        val sameSelection = sshHost.trim() == pending.sshHost && inventory.system == pending.os &&
            clientsSupported(clients) &&
            addresses.toSet() == pending.addresses.map(TargetAddress::value).toSet() &&
            protocols.toSet() == pending.protocols.toSet() &&
            pending.addresses.all { target ->
                target.value in when (target.family) {
                    AddressFamily.IPV4 -> inventory.ipv4Addresses
                    AddressFamily.IPV6 -> inventory.ipv6Addresses
                }
            }
        if (!sameSelection) return CompositePlanResult.Blocked(CompositePlanBlocker.PLAN_MISMATCH)
        return CompositePlanResult.Ready(pending)
    }
}

private fun isIpv4Literal(value: String): Boolean {
    val octets = value.split('.')
    return octets.size == 4 && octets.all { octet ->
        octet.isNotEmpty() && octet.all(Char::isDigit) &&
            (octet.length == 1 || octet.first() != '0') && octet.toIntOrNull() in 0..255
    }
}
