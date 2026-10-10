package app.ownbridge.core

import java.util.UUID

sealed interface ProxyCredential {
    data class Shadowsocks(val password: String) : ProxyCredential {
        init { require(TOKEN.matches(password)) }
    }

    data class VlessReality(
        val uuid: String,
        val publicKey: String,
        val shortId: String,
        val serverName: String,
    ) : ProxyCredential {
        init {
            require(UUID.fromString(uuid).toString() == uuid)
            require(REALITY_KEY.matches(publicKey))
            require(Regex("[0-9a-f]{16}").matches(shortId))
            require(Regex("[A-Za-z0-9.-]{1,253}").matches(serverName))
        }
    }

    data class Hysteria2(val password: String, val certificateFingerprint: String) : ProxyCredential {
        init {
            require(TOKEN.matches(password))
            require(Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}").matches(certificateFingerprint))
        }
    }
}

data class DeployedNode(val node: PlannedNode, val credential: ProxyCredential) {
    init {
        require(when (node.protocol) {
            ProxyProtocol.SHADOWSOCKS -> credential is ProxyCredential.Shadowsocks
            ProxyProtocol.VLESS -> credential is ProxyCredential.VlessReality
            ProxyProtocol.HYSTERIA2 -> credential is ProxyCredential.Hysteria2
        })
    }
}

data class NamedDeployedNode(val name: String, val deployed: DeployedNode)

/** A local Mihomo profile, not a hosted subscription URL or a verified client connection. */
class CompositeClashExport(private val deployed: List<DeployedNode>) {
    val namedNodes: List<NamedDeployedNode> = nameDeployedNodes(deployed)

    val clashYaml: String
        get() {
            val named = namedNodes
            return buildString {
                appendLine("proxies:")
                named.forEach { (name, item) ->
                    appendLine("  - name: $name")
                    appendLine("    server: '${item.node.address.value}'")
                    appendLine("    port: ${item.node.port}")
                    when (val credential = item.credential) {
                        is ProxyCredential.Shadowsocks -> {
                            appendLine("    type: ss")
                            appendLine("    cipher: aes-256-gcm")
                            appendLine("    password: ${credential.password}")
                            appendLine("    udp: true")
                        }
                        is ProxyCredential.VlessReality -> {
                            appendLine("    type: vless")
                            appendLine("    uuid: ${credential.uuid}")
                            appendLine("    udp: true")
                            appendLine("    tls: true")
                            appendLine("    network: tcp")
                            appendLine("    servername: ${credential.serverName}")
                            appendLine("    client-fingerprint: chrome")
                            appendLine("    reality-opts:")
                            appendLine("      public-key: ${credential.publicKey}")
                            appendLine("      short-id: ${credential.shortId}")
                        }
                        is ProxyCredential.Hysteria2 -> {
                            appendLine("    type: hysteria2")
                            appendLine("    password: ${credential.password}")
                            appendLine("    skip-cert-verify: true")
                            appendLine("    fingerprint: '${credential.certificateFingerprint}'")
                        }
                    }
                }
                appendLine("proxy-groups:")
                appendLine("  - name: OwnBridge")
                appendLine("    type: select")
                appendLine("    proxies:")
                named.forEach { (name, _) -> appendLine("      - $name") }
                appendLine("      - DIRECT")
                appendLine("rules:")
                appendLine("  - MATCH,OwnBridge")
            }
        }
}

private val TOKEN = Regex("[A-Za-z0-9_-]{32,128}")
private val REALITY_KEY = Regex("[A-Za-z0-9_-]{43}")
