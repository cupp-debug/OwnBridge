package app.ownbridge

import app.ownbridge.core.ServerInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSelectionTest {
    private val inventory = ServerInventory(
        system = "Ubuntu",
        ipv4Addresses = listOf("203.0.113.10"),
        ipv6Addresses = listOf("2001:db8::10"),
        listeningPorts = emptyList(),
        runningServices = emptyList(),
        firewall = "inactive",
    )

    @Test
    fun defaultsToOneAddressPerSupportedFamily() {
        val multipleIpv6 = inventory.copy(
            ipv6Addresses = listOf("2001:db8::10", "2001:db8::11", "2001:db8::12"),
        )

        assertEquals(
            setOf("203.0.113.10", "2001:db8::10"),
            defaultAddressSelection(multipleIpv6, includeIpv6 = true),
        )
        assertEquals(
            setOf("203.0.113.10"),
            defaultAddressSelection(multipleIpv6, includeIpv6 = false),
        )
    }

    @Test
    fun defaultsOnlyToAddressesActuallyPresent() {
        assertEquals(
            setOf("203.0.113.10"),
            defaultAddressSelection(inventory.copy(ipv6Addresses = emptyList()), includeIpv6 = true),
        )
        assertEquals(
            setOf("2001:db8::10"),
            defaultAddressSelection(inventory.copy(ipv4Addresses = emptyList()), includeIpv6 = true),
        )
        assertEquals(
            emptySet<String>(),
            defaultAddressSelection(inventory.copy(ipv4Addresses = emptyList()), includeIpv6 = false),
        )
    }

    @Test
    fun countsAddressesTimesProtocolsNotClients() {
        val allProtocols = ProtocolOption.entries.mapTo(mutableSetOf()) { it.id }
        val allClients = ClientOption.entries.mapTo(mutableSetOf()) { it.id }

        val oneAddress = buildPlanSelection(
            "203.0.113.10", inventory, setOf("203.0.113.10"), allProtocols, allClients,
        )
        val twoAddresses = buildPlanSelection(
            "203.0.113.10", inventory,
            setOf("203.0.113.10", "2001:db8::10"), allProtocols, allClients,
        )

        assertTrue(oneAddress.isComplete)
        assertEquals(3, oneAddress.possibleNodeCount)
        assertEquals(6, twoAddresses.possibleNodeCount)
        assertEquals(2, twoAddresses.clients.size)
    }

    @Test
    fun excludesAddressesNotFoundInInventoryAndRequiresEachSelection() {
        val selection = buildPlanSelection(
            "203.0.113.10", inventory,
            setOf("198.51.100.9"), setOf("VLESS"), setOf(ClientOption.CLASH.id),
        )

        assertFalse(selection.isComplete)
        assertTrue(selection.addresses.isEmpty())
        assertEquals(0, selection.possibleNodeCount)
    }

    @Test
    fun cannotConfirmWithoutSuccessfulInventory() {
        val selection = buildPlanSelection(
            "203.0.113.10", null,
            setOf("203.0.113.10"), setOf("VLESS"), setOf(ClientOption.CLASH.id),
        )

        assertFalse(selection.isComplete)
        assertTrue(selection.server.isEmpty())
    }
}
