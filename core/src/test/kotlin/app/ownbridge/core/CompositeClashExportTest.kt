package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeClashExportTest {
    private val ipv4 = TargetAddress("203.0.113.10", AddressFamily.IPV4)
    private val ipv6 = TargetAddress("2001:db8::10", AddressFamily.IPV6)
    private val pin = (1..32).joinToString(":") { "AB" }

    @Test fun exportsAllSixDualStackNodesWithProtocolSpecificFields() {
        val nodes = listOf(ipv4, ipv6).flatMapIndexed { addressIndex, address ->
            ProxyProtocol.entries.mapIndexed { protocolIndex, protocol ->
                DeployedNode(PlannedNode(address, protocol, 28443 + addressIndex * 3 + protocolIndex),
                    credential(protocol))
            }
        }
        val yaml = CompositeClashExport(nodes).clashYaml
        val names = CompositeClashExport(nodes).namedNodes.map { it.name }
        assertEquals(6, Regex("  - name: OwnBridge-").findAll(yaml).count())
        assertEquals(6, names.distinct().size)
        assertTrue(names.all { name -> "  - name: $name\n" in yaml })
        assertTrue("server: '2001:db8::10'" in yaml)
        assertTrue("client-fingerprint: chrome" in yaml)
        assertTrue("fingerprint: '$pin'" in yaml)
        assertTrue("type: select" in yaml)
        assertFalse("type: fallback" in yaml)
    }

    @Test fun partialExportContainsOnlyActuallyReadyNodes() {
        val ss = DeployedNode(PlannedNode(ipv4, ProxyProtocol.SHADOWSOCKS, 28443),
            credential(ProxyProtocol.SHADOWSOCKS))
        val yaml = CompositeClashExport(listOf(ss)).clashYaml
        assertTrue("OwnBridge-SS-IPv4-1" in yaml)
        assertFalse("OwnBridge-VLESS" in yaml)
        assertFalse("OwnBridge-HY2" in yaml)
    }

    @Test fun credentialTypeAndCertificatePinAreMandatory() {
        assertThrows(IllegalArgumentException::class.java) {
            DeployedNode(PlannedNode(ipv4, ProxyProtocol.HYSTERIA2, 28443),
                credential(ProxyProtocol.SHADOWSOCKS))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProxyCredential.Hysteria2("a".repeat(32), "")
        }
    }

    private fun credential(protocol: ProxyProtocol): ProxyCredential = when (protocol) {
        ProxyProtocol.SHADOWSOCKS -> ProxyCredential.Shadowsocks("a".repeat(32))
        ProxyProtocol.VLESS -> ProxyCredential.VlessReality(
            "bf000d23-0752-40b4-affe-68f7707a9661", "B".repeat(43),
            "0123456789abcdef", "www.cloudflare.com",
        )
        ProxyProtocol.HYSTERIA2 -> ProxyCredential.Hysteria2("b".repeat(32), pin)
    }
}
