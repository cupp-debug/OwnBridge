package app.ownbridge

import app.ownbridge.core.AddressFamily
import app.ownbridge.core.DeployedNode
import app.ownbridge.core.NamedDeployedNode
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProxyCredential
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.TargetAddress
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv6NodeProbePolicyTest {
    private val nodes = listOf(
        NamedDeployedNode("IPv4", DeployedNode(
            PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4), ProxyProtocol.SHADOWSOCKS, 28443),
            ProxyCredential.Shadowsocks("a".repeat(32)),
        )),
        NamedDeployedNode("IPv6", DeployedNode(
            PlannedNode(TargetAddress("2001:db8::10", AddressFamily.IPV6), ProxyProtocol.SHADOWSOCKS, 28444),
            ProxyCredential.Shadowsocks("b".repeat(32)),
        )),
    )

    @Test fun onlyDefinitivelyUnavailableIpv6IsSkipped() {
        assertEquals(listOf(NodeProbeStatus.WAITING, NodeProbeStatus.IPV6_UNAVAILABLE),
            prepareNodeProbeItems(nodes, Ipv6NetworkAvailability.UNAVAILABLE).map(NodeProbeItem::status))
        for (availability in listOf(Ipv6NetworkAvailability.AVAILABLE, Ipv6NetworkAvailability.UNKNOWN)) {
            assertEquals(listOf(NodeProbeStatus.WAITING, NodeProbeStatus.WAITING),
                prepareNodeProbeItems(nodes, availability).map(NodeProbeItem::status))
        }
    }

    @Test fun routeAndUsableSourceAreBothRequired() {
        assertEquals(Ipv6NetworkAvailability.AVAILABLE,
            Ipv6NetworkAvailability.from(hasUsableAddress = true, hasDefaultRoute = true))
        assertEquals(Ipv6NetworkAvailability.UNAVAILABLE,
            Ipv6NetworkAvailability.from(hasUsableAddress = false, hasDefaultRoute = true))
        assertEquals(Ipv6NetworkAvailability.UNAVAILABLE,
            Ipv6NetworkAvailability.from(hasUsableAddress = true, hasDefaultRoute = false))
    }

    @Test fun linkLocalAndLoopbackAreNotPublicIpv6Sources() {
        assertTrue(isUsableIpv6Source(InetAddress.getByName("2001:db8::1")))
        assertFalse(isUsableIpv6Source(InetAddress.getByName("fe80::1")))
        assertFalse(isUsableIpv6Source(InetAddress.getByName("::1")))
        assertFalse(isUsableIpv6Source(InetAddress.getByName("203.0.113.10")))
    }
}
