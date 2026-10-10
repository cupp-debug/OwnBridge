package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CompositeDeploymentPlanTest {
    private val inventory = ServerInventory(
        system = "Ubuntu 26.04.1 LTS",
        ipv4Addresses = listOf("203.0.113.10"),
        ipv6Addresses = listOf("2001:db8::10"),
        listeningPorts = listOf(ListeningPort("tcp", "0.0.0.0", 28443, "other")),
        runningServices = emptyList(),
        firewall = "Status: active",
    )
    private val all = ProxyProtocol.entries.toList()
    private val allCapabilities = DeploymentCapabilities(all.toSet(), maxIpv6Addresses = 1)

    @Test fun supportsUbuntu2204Alongside2404And2604() {
        for (system in listOf("Ubuntu 22.04 LTS", "Ubuntu 22.04.5 LTS",
            "Ubuntu 24.04 LTS", "Ubuntu 24.04.1 LTS", "Ubuntu 26.04.1 LTS")) {
            assertTrue(system, plan(server = inventory.copy(system = system)) is CompositePlanResult.Ready)
        }
        for (system in listOf("Ubuntu 20.04 LTS", "Ubuntu 24.040 LTS", "Debian 12")) {
            assertEquals(system, CompositePlanBlocker.UNSUPPORTED_SYSTEM,
                (plan(server = inventory.copy(system = system)) as CompositePlanResult.Blocked).reason)
        }
    }

    @Test fun everyNonEmptyProtocolSubsetProducesOnlyItsSelectedNodes() {
        for (mask in 1 until (1 shl all.size)) {
            val selected = all.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val result = plan(protocols = selected)
            assertTrue(result is CompositePlanResult.Ready)
            val nodes = (result as CompositePlanResult.Ready).plan.nodes
            assertEquals(selected, nodes.map(PlannedNode::protocol))
            assertEquals((28444 until 28444 + selected.size).toList(), nodes.map(PlannedNode::port))
        }
    }

    @Test fun dualStackExpandsTargetsWithoutChangingProtocolOrder() {
        val result = plan(addresses = listOf("203.0.113.10", "2001:db8::10"))
        assertTrue(result is CompositePlanResult.Ready)
        val nodes = (result as CompositePlanResult.Ready).plan.nodes
        assertEquals(6, nodes.size)
        assertEquals(listOf(AddressFamily.IPV4, AddressFamily.IPV6),
            nodes.map { it.address.family }.distinct())
        assertEquals(all + all, nodes.map(PlannedNode::protocol))
        assertEquals(6, nodes.map(PlannedNode::port).distinct().size)
        assertEquals(Transport.TCP_AND_UDP, nodes.first().transport)
        assertEquals(Transport.TCP, nodes[1].transport)
        assertEquals(Transport.UDP, nodes[2].transport)
    }

    @Test fun clientExportChoiceDoesNotChangeServerPlan() {
        val clash = plan() as CompositePlanResult.Ready
        for (clients in listOf(listOf("v2rayN / v2rayNG"),
            listOf("Clash / Mihomo", "v2rayN / v2rayNG"))) {
            val created = CompositeDeploymentPlanner.create("203.0.113.10", "root", inventory,
                listOf("203.0.113.10"), all, clients, allCapabilities)
            assertEquals(clash.plan, (created as CompositePlanResult.Ready).plan)
            assertEquals(clash.plan, (CompositeDeploymentPlanner.resume("203.0.113.10", "root",
                inventory, listOf("203.0.113.10"), all, clients, allCapabilities,
                clash.plan) as CompositePlanResult.Ready).plan)
        }
        val unsupported = CompositeDeploymentPlanner.create("203.0.113.10", "root", inventory,
            listOf("203.0.113.10"), all, listOf("Shadowrocket"), allCapabilities)
        assertEquals(CompositePlanBlocker.UNSUPPORTED_CLIENT,
            (unsupported as CompositePlanResult.Blocked).reason)
    }

    @Test fun aPartialAddressProtocolMatrixCannotMasqueradeAsTheSelectedPlan() {
        assertThrows(IllegalArgumentException::class.java) {
            CompositeDeploymentPlan("203.0.113.10", "Ubuntu 26.04", listOf(
                PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4),
                    ProxyProtocol.SHADOWSOCKS, 28443),
                PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4),
                    ProxyProtocol.VLESS, 28444),
                PlannedNode(TargetAddress("2001:db8::10", AddressFamily.IPV6),
                    ProxyProtocol.SHADOWSOCKS, 28445),
            ))
        }
    }

    @Test fun unsupportedCapabilitiesStayBlockedRatherThanSilentlyOmitted() {
        assertEquals(CompositePlanBlocker.UNSUPPORTED_PROTOCOL,
            (plan(capabilities = DeploymentCapabilities.ExistingSsOnly) as CompositePlanResult.Blocked).reason)
        assertEquals(CompositePlanBlocker.IPV6_NOT_ENABLED,
            (plan(addresses = listOf("2001:db8::10"), protocols = listOf(ProxyProtocol.SHADOWSOCKS),
                capabilities = DeploymentCapabilities.ExistingSsOnly) as CompositePlanResult.Blocked).reason)
    }

    @Test fun unverifiedExtraAddressesAreBlockedInBothNewAndResumedRuns() {
        val expanded = inventory.copy(
            ipv4Addresses = inventory.ipv4Addresses + "203.0.113.11",
            ipv6Addresses = inventory.ipv6Addresses + "2001:db8::11",
        )
        assertEquals(CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED,
            (plan(server = expanded, addresses = expanded.ipv4Addresses)
                as CompositePlanResult.Blocked).reason)
        assertEquals(CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED,
            (plan(server = expanded, addresses = listOf("203.0.113.10") + expanded.ipv6Addresses)
                as CompositePlanResult.Blocked).reason)

        val widerGate = allCapabilities.copy(maxIpv4Addresses = 2)
        val saved = (plan(server = expanded, addresses = expanded.ipv4Addresses,
            capabilities = widerGate) as CompositePlanResult.Ready).plan
        val resumed = CompositeDeploymentPlanner.resume(
            "203.0.113.10", "root", expanded, expanded.ipv4Addresses, all,
            listOf("Clash / Mihomo"), allCapabilities, saved,
        )
        assertEquals(CompositePlanBlocker.ADDRESS_LIMIT_NOT_VERIFIED,
            (resumed as CompositePlanResult.Blocked).reason)
    }

    @Test fun unknownOrMalformedInventoryAddressIsRejected() {
        assertEquals(CompositePlanBlocker.INVALID_ADDRESS,
            (plan(addresses = listOf("203.0.113.11")) as CompositePlanResult.Blocked).reason)
        val malformed = inventory.copy(ipv6Addresses = listOf("not-an-ipv6"))
        assertEquals(CompositePlanBlocker.INVALID_ADDRESS,
            (plan(server = malformed, addresses = listOf("not-an-ipv6"))
                as CompositePlanResult.Blocked).reason)
    }

    @Test fun portExhaustionBlocksButExistingServiceCanCoexistOnOtherPorts() {
        val full = inventory.copy(listeningPorts = (28443..28542).map {
            ListeningPort("udp", "::", it, "other")
        })
        assertEquals(CompositePlanBlocker.NO_AVAILABLE_PORT,
            (plan(server = full) as CompositePlanResult.Blocked).reason)
        val legacy = inventory.copy(runningServices = listOf("ownbridge-ss.service"))
        assertTrue(plan(server = legacy) is CompositePlanResult.Ready)
        val existing = inventory.copy(runningServices = listOf("ownbridge-v2-ss.service"),
            listeningPorts = inventory.listeningPorts + ListeningPort("tcp", "203.0.113.10", 28444, "sing-box"))
        val additional = plan(server = existing)
        assertTrue(additional is CompositePlanResult.Ready)
        assertEquals(28445, (additional as CompositePlanResult.Ready).plan.nodes.first().port)
    }

    @Test fun resumeKeepsPortsButRejectsChangedSelection() {
        val pending = (plan() as CompositePlanResult.Ready).plan
        val withOwnedListeners = inventory.copy(
            runningServices = listOf("ownbridge-ss.service", "ownbridge-vless.service"),
            listeningPorts = pending.nodes.map {
                ListeningPort("tcp", it.address.value, it.port, "sing-box")
            },
        )
        val resumed = CompositeDeploymentPlanner.resume(
            "203.0.113.10", "root", withOwnedListeners, listOf("203.0.113.10"),
            all, listOf("Clash / Mihomo"), allCapabilities, pending,
        )
        assertEquals(pending, (resumed as CompositePlanResult.Ready).plan)
        val reordered = CompositeDeploymentPlanner.resume(
            "203.0.113.10", "root", withOwnedListeners, listOf("203.0.113.10"),
            listOf(ProxyProtocol.VLESS, ProxyProtocol.SHADOWSOCKS, ProxyProtocol.HYSTERIA2),
            listOf("Clash / Mihomo"), allCapabilities, pending,
        )
        assertEquals(pending, (reordered as CompositePlanResult.Ready).plan)
        val changed = CompositeDeploymentPlanner.resume(
            "203.0.113.10", "root", withOwnedListeners, listOf("2001:db8::10"),
            all, listOf("Clash / Mihomo"), allCapabilities, pending,
        )
        assertEquals(CompositePlanBlocker.PLAN_MISMATCH,
            (changed as CompositePlanResult.Blocked).reason)
    }

    private fun plan(
        server: ServerInventory = inventory,
        addresses: List<String> = listOf("203.0.113.10"),
        protocols: List<ProxyProtocol> = all,
        capabilities: DeploymentCapabilities = allCapabilities,
    ) = CompositeDeploymentPlanner.create(
        sshHost = "203.0.113.10",
        username = "root",
        inventory = server,
        addresses = addresses,
        protocols = protocols,
        clients = listOf("Clash / Mihomo"),
        capabilities = capabilities,
    )
}
