package app.ownbridge.core

/** Stable display names shared by every client export format. */
internal fun nameDeployedNodes(deployed: List<DeployedNode>): List<NamedDeployedNode> {
    require(deployed.isNotEmpty())
    require(deployed.distinctBy { it.node.address to it.node.protocol }.size == deployed.size)
    val ordinals = mutableMapOf<Pair<ProxyProtocol, AddressFamily>, Int>()
    return deployed.map { item ->
        val key = item.node.protocol to item.node.address.family
        val ordinal = (ordinals[key] ?: 0) + 1
        ordinals[key] = ordinal
        val family = if (item.node.address.family == AddressFamily.IPV4) "IPv4" else "IPv6"
        val protocol = when (item.node.protocol) {
            ProxyProtocol.SHADOWSOCKS -> "SS"
            ProxyProtocol.VLESS -> "VLESS"
            ProxyProtocol.HYSTERIA2 -> "HY2"
        }
        NamedDeployedNode("OwnBridge-$protocol-$family-$ordinal", item)
    }
}
