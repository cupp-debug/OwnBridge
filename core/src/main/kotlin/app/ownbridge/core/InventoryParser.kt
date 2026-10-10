package app.ownbridge.core

/** Parses only the fixed, read-only probes in [ReadOnlyProbes]. */
object InventoryParser {
    fun system(output: String): String = output.lineSequence()
        .firstOrNull { it.startsWith("PRETTY_NAME=") }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
        ?.takeIf(String::isNotBlank)
        ?: "Unknown Linux"

    fun addresses(output: String): Pair<List<String>, List<String>> {
        val ipv4 = linkedSetOf<String>()
        val ipv6 = linkedSetOf<String>()
        output.lineSequence().forEach { line ->
            if (!line.contains("scope global")) return@forEach
            val fields = line.trim().split(Regex("\\s+"))
            val familyIndex = fields.indexOfFirst { it == "inet" || it == "inet6" }
            val address = fields.getOrNull(familyIndex + 1)?.substringBefore('/')
            if (familyIndex >= 0 && !address.isNullOrBlank()) {
                when (fields[familyIndex]) {
                    "inet" -> ipv4 += address
                    "inet6" -> ipv6 += address
                }
            }
        }
        return ipv4.toList() to ipv6.toList()
    }

    fun ports(output: String): List<ListeningPort> = output.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"), limit = 7)
        val protocol = fields.firstOrNull() ?: return@mapNotNull null
        if (!protocol.startsWith("tcp") && !protocol.startsWith("udp")) return@mapNotNull null
        val endpoint = fields.getOrNull(4) ?: return@mapNotNull null
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
        val address = endpoint.substringBeforeLast(':').removeSurrounding("[", "]")
        val process = Regex("users:\\(\\(\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "—"
        ListeningPort(protocol, address, port, process)
    }.toList()

    fun services(output: String): List<String> = output.lineSequence()
        .map { it.trim().substringBefore(' ') }
        .filter { it.endsWith(".service") }
        .distinct()
        .toList()
}
