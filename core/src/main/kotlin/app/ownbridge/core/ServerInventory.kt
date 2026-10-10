package app.ownbridge.core

data class ListeningPort(
    val protocol: String,
    val address: String,
    val port: Int,
    val process: String,
)

data class ServerInventory(
    val system: String,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val listeningPorts: List<ListeningPort>,
    val runningServices: List<String>,
    val firewall: String,
)
