package app.ownbridge

import app.ownbridge.core.CompositeDeploymentJournal
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.DeployedNode
import app.ownbridge.core.ProtocolProgress
import app.ownbridge.core.ProxyCredential
import app.ownbridge.core.ProxyProtocol
import org.json.JSONObject

internal data class CompositeCredentialReadResult(
    val nodes: List<DeployedNode>,
    val failedProtocols: Set<ProxyProtocol>,
)

/** Reads only client-side exports for protocols whose server verification finished. */
internal class CompositeCredentialReader(private val read: (String) -> ByteArray) {
    fun readReady(
        runDirectory: String,
        plan: CompositeDeploymentPlan,
        journal: CompositeDeploymentJournal,
    ): CompositeCredentialReadResult {
        val nodes = mutableListOf<DeployedNode>()
        val failed = mutableSetOf<ProxyProtocol>()
        for (protocol in ProxyProtocol.entries.filter { it in plan.protocols }) {
            if (journal.protocolProgress(protocol) != ProtocolProgress.SERVER_READY) continue
            val protocolNodes = try {
                plan.nodes.filter { it.protocol == protocol }.mapIndexed { index, node ->
                    val name = "%s-%02d.json".format(java.util.Locale.ROOT, protocol.wireName, index + 1)
                    val export = JSONObject(read("$runDirectory/exports/$name").toString(Charsets.UTF_8))
                    require(export.getString("type") == protocol.wireName) { "Wrong credential type" }
                    val expectedFields = when (protocol) {
                        ProxyProtocol.SHADOWSOCKS -> setOf("type", "password")
                        ProxyProtocol.VLESS -> setOf("type", "uuid", "public_key", "short_id", "server_name")
                        ProxyProtocol.HYSTERIA2 -> setOf("type", "password", "fingerprint")
                    }
                    require(export.keys().asSequence().toSet() == expectedFields) {
                        "Unexpected client export fields"
                    }
                    val credential = when (protocol) {
                        ProxyProtocol.SHADOWSOCKS -> ProxyCredential.Shadowsocks(export.getString("password"))
                        ProxyProtocol.VLESS -> ProxyCredential.VlessReality(
                            uuid = export.getString("uuid"),
                            publicKey = export.getString("public_key"),
                            shortId = export.getString("short_id"),
                            serverName = export.getString("server_name"),
                        )
                        ProxyProtocol.HYSTERIA2 -> ProxyCredential.Hysteria2(
                            password = export.getString("password"),
                            certificateFingerprint = export.getString("fingerprint"),
                        )
                    }
                    DeployedNode(node, credential)
                }
            } catch (_: Exception) {
                null
            }
            if (protocolNodes == null) failed += protocol else nodes += protocolNodes
        }
        return CompositeCredentialReadResult(nodes, failed)
    }
}
