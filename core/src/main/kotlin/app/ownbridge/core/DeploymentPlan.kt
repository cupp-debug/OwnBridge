package app.ownbridge.core

data class ShadowsocksPlan(
    val sshHost: String,
    val address: String,
    val port: Int,
    val os: String,
) {
    val protocol: String = "SS"
    val client: String = "Clash / Mihomo"
}

enum class PlanBlocker {
    NO_INVENTORY,
    ROOT_REQUIRED,
    UNSUPPORTED_SYSTEM,
    UNSUPPORTED_SELECTION,
    INVALID_ADDRESS,
    NO_AVAILABLE_PORT,
    EXISTING_OWNBRIDGE_SERVICE,
}

sealed interface DeploymentPlanResult {
    data class Ready(val plan: ShadowsocksPlan) : DeploymentPlanResult
    data class Blocked(val reason: PlanBlocker) : DeploymentPlanResult
}

object DeploymentPlanner {
    private val candidatePorts = 28443..28542

    fun create(
        sshHost: String,
        username: String,
        inventory: ServerInventory?,
        addresses: List<String>,
        protocols: List<String>,
        clients: List<String>,
    ): DeploymentPlanResult {
        if (inventory == null) return DeploymentPlanResult.Blocked(PlanBlocker.NO_INVENTORY)
        if (username.trim() != "root") return DeploymentPlanResult.Blocked(PlanBlocker.ROOT_REQUIRED)
        if (!SupportedUbuntu.matches(inventory.system)) {
            return DeploymentPlanResult.Blocked(PlanBlocker.UNSUPPORTED_SYSTEM)
        }
        if (addresses.size != 1 || protocols != listOf("SS") || clients != listOf("Clash / Mihomo")) {
            return DeploymentPlanResult.Blocked(PlanBlocker.UNSUPPORTED_SELECTION)
        }
        val address = addresses.single()
        if (address !in inventory.ipv4Addresses || !isIpv4(address)) {
            return DeploymentPlanResult.Blocked(PlanBlocker.INVALID_ADDRESS)
        }
        if (inventory.runningServices.any { it == "ownbridge-ss.service" }) {
            return DeploymentPlanResult.Blocked(PlanBlocker.EXISTING_OWNBRIDGE_SERVICE)
        }
        val occupied = inventory.listeningPorts.mapTo(mutableSetOf()) { it.port }
        val port = candidatePorts.firstOrNull { it !in occupied }
            ?: return DeploymentPlanResult.Blocked(PlanBlocker.NO_AVAILABLE_PORT)
        return DeploymentPlanResult.Ready(
            ShadowsocksPlan(sshHost.trim(), address, port, inventory.system),
        )
    }

    fun resume(
        sshHost: String,
        username: String,
        inventory: ServerInventory?,
        addresses: List<String>,
        protocols: List<String>,
        clients: List<String>,
        pending: ShadowsocksPlan,
    ): DeploymentPlanResult {
        if (inventory == null) return DeploymentPlanResult.Blocked(PlanBlocker.NO_INVENTORY)
        if (username.trim() != "root") return DeploymentPlanResult.Blocked(PlanBlocker.ROOT_REQUIRED)
        if (
            sshHost.trim() != pending.sshHost || inventory.system != pending.os ||
            addresses != listOf(pending.address) || protocols != listOf(pending.protocol) ||
            clients != listOf(pending.client) || pending.address !in inventory.ipv4Addresses
        ) {
            return DeploymentPlanResult.Blocked(PlanBlocker.UNSUPPORTED_SELECTION)
        }
        return DeploymentPlanResult.Ready(pending)
    }

    private fun isIpv4(address: String): Boolean {
        val octets = address.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.all(Char::isDigit) &&
                (octet.length == 1 || octet.first() != '0') &&
                octet.toIntOrNull() in 0..255
        }
    }
}
