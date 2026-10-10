package app.ownbridge

import app.ownbridge.core.ServerInventory

internal data class PlanSelection(
    val server: String,
    val addresses: List<String>,
    val protocols: List<String>,
    val clients: List<String>,
) {
    val isComplete: Boolean
        get() = server.isNotBlank() && addresses.isNotEmpty() &&
            protocols.isNotEmpty() && clients.isNotEmpty()

    val possibleNodeCount: Int
        get() = addresses.size * protocols.size
}

internal fun defaultAddressSelection(
    inventory: ServerInventory,
    includeIpv6: Boolean,
): Set<String> = listOfNotNull(
    inventory.ipv4Addresses.firstOrNull(),
    if (includeIpv6) inventory.ipv6Addresses.firstOrNull() else null,
).toSet()

internal fun buildPlanSelection(
    host: String,
    inventory: ServerInventory?,
    selectedAddresses: Set<String>,
    selectedProtocols: Set<String>,
    selectedClients: Set<String>,
): PlanSelection = PlanSelection(
    server = if (inventory == null) "" else host.trim(),
    addresses = inventory?.targetAddresses().orEmpty().filter { it in selectedAddresses },
    protocols = ProtocolOption.entries.map { it.id }.filter { it in selectedProtocols },
    clients = ClientOption.entries.map { it.id }.filter { it in selectedClients },
)
