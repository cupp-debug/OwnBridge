package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeploymentPlanTest {
    private val inventory = ServerInventory(
        system = "Ubuntu 26.04.1 LTS",
        ipv4Addresses = listOf("203.0.113.10"),
        ipv6Addresses = listOf("2001:db8::10"),
        listeningPorts = listOf(ListeningPort("tcp", "0.0.0.0", 28443, "other")),
        runningServices = emptyList(),
        firewall = "Status: active",
    )

    @Test fun choosesFirstUnoccupiedPort() {
        val result = plan(inventory)
        assertTrue(result is DeploymentPlanResult.Ready)
        assertEquals(28444, (result as DeploymentPlanResult.Ready).plan.port)
    }

    @Test fun rejectsUnimplementedSelectionsAndUnknownAddresses() {
        assertEquals(
            PlanBlocker.UNSUPPORTED_SELECTION,
            (DeploymentPlanner.create(
                "203.0.113.10", "root", inventory, listOf("203.0.113.10"),
                listOf("VLESS"), listOf("Clash / Mihomo"),
            ) as DeploymentPlanResult.Blocked).reason,
        )
        assertEquals(
            PlanBlocker.INVALID_ADDRESS,
            (DeploymentPlanner.create(
                "203.0.113.10", "root", inventory, listOf("203.0.113.11"),
                listOf("SS"), listOf("Clash / Mihomo"),
            ) as DeploymentPlanResult.Blocked).reason,
        )
    }

    @Test fun acceptsUbuntu2204And2404AndRejectsUnsupportedSystemOrOwnedService() {
        assertTrue(plan(inventory.copy(system = "Ubuntu 22.04.5 LTS")) is DeploymentPlanResult.Ready)
        assertTrue(plan(inventory.copy(system = "Ubuntu 24.04.1 LTS")) is DeploymentPlanResult.Ready)
        assertEquals(
            PlanBlocker.UNSUPPORTED_SYSTEM,
            (plan(inventory.copy(system = "Ubuntu 20.04.6 LTS")) as DeploymentPlanResult.Blocked).reason,
        )
        assertEquals(
            PlanBlocker.EXISTING_OWNBRIDGE_SERVICE,
            (plan(inventory.copy(runningServices = listOf("ownbridge-ss.service")))
                as DeploymentPlanResult.Blocked).reason,
        )
    }

    @Test fun rootAndFreePortAreRequired() {
        assertEquals(
            PlanBlocker.ROOT_REQUIRED,
            (DeploymentPlanner.create(
                "203.0.113.10", "ubuntu", inventory, listOf("203.0.113.10"),
                listOf("SS"), listOf("Clash / Mihomo"),
            ) as DeploymentPlanResult.Blocked).reason,
        )
        val full = inventory.copy(listeningPorts = (28443..28542).map {
            ListeningPort("tcp", "0.0.0.0", it, "other")
        })
        assertEquals(
            PlanBlocker.NO_AVAILABLE_PORT,
            (plan(full) as DeploymentPlanResult.Blocked).reason,
        )
    }

    @Test fun resumeRetainsPortOnlyForTheExactEarlierPlan() {
        val pending = ShadowsocksPlan("203.0.113.10", "203.0.113.10", 28443, inventory.system)
        val same = DeploymentPlanner.resume(
            "203.0.113.10", "root", inventory, listOf("203.0.113.10"),
            listOf("SS"), listOf("Clash / Mihomo"), pending,
        )
        assertEquals(pending, (same as DeploymentPlanResult.Ready).plan)
        val changed = DeploymentPlanner.resume(
            "203.0.113.10", "root", inventory, listOf("2001:db8::10"),
            listOf("SS"), listOf("Clash / Mihomo"), pending,
        )
        assertEquals(PlanBlocker.UNSUPPORTED_SELECTION, (changed as DeploymentPlanResult.Blocked).reason)
    }

    private fun plan(server: ServerInventory) = DeploymentPlanner.create(
        "203.0.113.10", "root", server, listOf("203.0.113.10"), listOf("SS"), listOf("Clash / Mihomo"),
    )
}
