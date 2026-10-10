package app.ownbridge.core

import java.net.URLEncoder
import java.util.Base64

/** Native share-link list for v2rayN / v2rayNG; no Clash YAML conversion. */
class CompositeV2RayExport(deployed: List<DeployedNode>) {
    val namedNodes: List<NamedDeployedNode> = nameDeployedNodes(deployed)

    val shareLinks: String
        get() = namedNodes.joinToString(separator = "\n", postfix = "\n") { (name, item) ->
            val node = item.node
            val host = if (node.address.family == AddressFamily.IPV6) "[${node.address.value}]"
                else node.address.value
            val endpoint = "$host:${node.port}"
            val fragment = encode(name)
            when (val credential = item.credential) {
                is ProxyCredential.Shadowsocks -> {
                    val userInfo = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        "aes-256-gcm:${credential.password}".toByteArray(Charsets.UTF_8))
                    "ss://$userInfo@$endpoint#$fragment"
                }
                is ProxyCredential.VlessReality ->
                    "vless://${credential.uuid}@$endpoint" +
                        "?encryption=none&security=reality&type=tcp" +
                        "&sni=${encode(credential.serverName)}&fp=chrome" +
                        "&pbk=${encode(credential.publicKey)}&sid=${encode(credential.shortId)}" +
                        "#$fragment"
                is ProxyCredential.Hysteria2 ->
                    "hysteria2://${encode(credential.password)}@$endpoint/" +
                        "?insecure=1&pinSHA256=${credential.certificateFingerprint.replace(":", "")}" +
                        "#$fragment"
            }
        }
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    .replace("+", "%20")
