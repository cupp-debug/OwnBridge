package app.ownbridge.core

/** Accepts only the exact profile shape emitted by [CompositeClashExport]. Never runs arbitrary YAML. */
object OwnBridgeYamlImport {
    private const val MAX_CHARS = 65_536
    private const val MAX_NODES = 32

    fun parse(raw: String): CompositeClashExport {
        require(raw.length in 1..MAX_CHARS) { "Invalid OwnBridge profile" }
        val normalized = raw.replace("\r\n", "\n").trimEnd('\n') + "\n"
        require('\r' !in normalized && '\u0000' !in normalized && '\t' !in normalized)
        val lines = normalized.lines()
        require(lines.firstOrNull() == "proxies:")
        val groupStart = lines.indexOf("proxy-groups:")
        require(groupStart > 1)
        val blocks = mutableListOf<List<String>>()
        var block = mutableListOf<String>()
        for (line in lines.subList(1, groupStart)) {
            if (line.startsWith("  - name: ")) {
                if (block.isNotEmpty()) blocks += block
                block = mutableListOf()
            }
            require(block.isNotEmpty() || line.startsWith("  - name: "))
            block += line
        }
        if (block.isNotEmpty()) blocks += block
        require(blocks.size in 1..MAX_NODES)
        val nodes = blocks.map(::parseNode)
        val export = CompositeClashExport(nodes)
        require(export.clashYaml == normalized) { "Unsupported OwnBridge profile format" }
        return export
    }

    private fun parseNode(lines: List<String>): DeployedNode {
        fun field(index: Int, prefix: String): String {
            val line = lines.getOrNull(index) ?: throw IllegalArgumentException("Invalid node")
            require(line.startsWith(prefix))
            return line.removePrefix(prefix)
        }
        field(0, "  - name: ") // Canonical comparison below checks the generated name.
        val quotedAddress = field(1, "    server: ")
        require(quotedAddress.length >= 3 && quotedAddress.first() == '\'' && quotedAddress.last() == '\'')
        val addressText = quotedAddress.substring(1, quotedAddress.lastIndex)
        val family = if (':' in addressText) AddressFamily.IPV6 else AddressFamily.IPV4
        val address = TargetAddress(addressText, family)
        val port = field(2, "    port: ").toInt()
        val type = field(3, "    type: ")
        val (protocol, credential, size) = when (type) {
            "ss" -> {
                require(field(4, "    cipher: ") == "aes-256-gcm")
                val password = field(5, "    password: ")
                require(field(6, "    udp: ") == "true")
                Triple(ProxyProtocol.SHADOWSOCKS, ProxyCredential.Shadowsocks(password), 7)
            }
            "vless" -> {
                val uuid = field(4, "    uuid: ")
                require(field(5, "    udp: ") == "true")
                require(field(6, "    tls: ") == "true")
                require(field(7, "    network: ") == "tcp")
                val serverName = field(8, "    servername: ")
                require(field(9, "    client-fingerprint: ") == "chrome")
                require(lines.getOrNull(10) == "    reality-opts:")
                val publicKey = field(11, "      public-key: ")
                val shortId = field(12, "      short-id: ")
                Triple(ProxyProtocol.VLESS,
                    ProxyCredential.VlessReality(uuid, publicKey, shortId, serverName), 13)
            }
            "hysteria2" -> {
                val password = field(4, "    password: ")
                require(field(5, "    skip-cert-verify: ") == "true")
                val fingerprint = field(6, "    fingerprint: ")
                require(fingerprint.length >= 3 && fingerprint.first() == '\'' && fingerprint.last() == '\'')
                Triple(ProxyProtocol.HYSTERIA2,
                    ProxyCredential.Hysteria2(password, fingerprint.substring(1, fingerprint.lastIndex)), 7)
            }
            else -> throw IllegalArgumentException("Unsupported node type")
        }
        require(lines.size == size)
        return DeployedNode(PlannedNode(address, protocol, port), credential)
    }
}
