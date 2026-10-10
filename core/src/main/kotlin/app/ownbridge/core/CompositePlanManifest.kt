package app.ownbridge.core

import java.security.MessageDigest

/** The deliberately small, canonical, non-secret input uploaded alongside the v2 package. */
object CompositePlanManifest {
    private const val HEADER = "ownbridge-plan-v2"

    fun bytes(plan: CompositeDeploymentPlan): ByteArray = buildString {
        appendLine(HEADER)
        plan.nodes.forEach { node ->
            append(node.address.family.name.lowercase())
            append(' ')
            append(node.address.value)
            append(' ')
            append(node.protocol.wireName)
            append(' ')
            appendLine(node.port)
        }
    }.toByteArray(Charsets.US_ASCII)

    fun sha256(plan: CompositeDeploymentPlan): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes(plan)).joinToString("") { "%02x".format(it) }

    /** Reconstructs a saved plan without inventing new ports or trusting malformed local state. */
    fun parse(sshHost: String, os: String, content: ByteArray): CompositeDeploymentPlan {
        require(content.size in 1..16_384 && content.last() == '\n'.code.toByte())
        require(content.all { it.toInt() in 0..127 })
        val lines = content.toString(Charsets.US_ASCII).trimEnd('\n').split('\n')
        require(lines.first() == HEADER && lines.size in 2..101)
        val nodes = lines.drop(1).map { line ->
            val fields = line.split(' ')
            require(fields.size == 4 && fields.all(String::isNotEmpty))
            val family = when (fields[0]) {
                "ipv4" -> AddressFamily.IPV4
                "ipv6" -> AddressFamily.IPV6
                else -> error("Unknown address family")
            }
            val protocol = ProxyProtocol.entries.singleOrNull { it.wireName == fields[2] }
                ?: error("Unknown protocol")
            val port = fields[3].toIntOrNull() ?: error("Invalid port")
            PlannedNode(TargetAddress(fields[1], family), protocol, port)
        }
        val plan = CompositeDeploymentPlan(sshHost, os, nodes)
        require(bytes(plan).contentEquals(content)) { "Non-canonical plan manifest" }
        return plan
    }
}
