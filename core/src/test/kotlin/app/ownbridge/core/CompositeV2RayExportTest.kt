package app.ownbridge.core

import java.net.URI
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeV2RayExportTest {
    private val ipv4 = TargetAddress("203.0.113.10", AddressFamily.IPV4)
    private val ipv6 = TargetAddress("2001:db8::10", AddressFamily.IPV6)
    private val fingerprint = List(32) { "AB" }.joinToString(":")

    @Test fun sixLinksUseProtocolSpecificCredentialsAndBracketIpv6() {
        val deployed = listOf(ipv4, ipv6).flatMapIndexed { addressIndex, address ->
            ProxyProtocol.entries.mapIndexed { protocolIndex, protocol ->
                DeployedNode(PlannedNode(address, protocol, 28443 + addressIndex * 3 + protocolIndex),
                    credential(protocol))
            }
        }
        val exported = CompositeV2RayExport(deployed)
        val lines = exported.shareLinks.trimEnd().lines()
        assertEquals(6, lines.size)
        assertEquals(CompositeClashExport(deployed).namedNodes.map { it.name },
            exported.namedNodes.map { it.name })
        assertEquals("203.0.113.10", URI(lines[0]).host)
        assertEquals("[2001:db8::10]", URI(lines[3]).rawAuthority.substringAfter('@').substringBeforeLast(':'))
        assertEquals("aes-256-gcm:${"a".repeat(32)}", String(Base64.getUrlDecoder()
            .decode(URI(lines[0]).rawUserInfo), Charsets.UTF_8))
        assertTrue(lines[1].startsWith("vless://bf000d23-0752-40b4-affe-68f7707a9661@"))
        assertTrue("encryption=none&security=reality&type=tcp" in lines[1])
        assertTrue("sni=www.cloudflare.com&fp=chrome&pbk=${"B".repeat(43)}&sid=0123456789abcdef" in lines[1])
        assertTrue(lines[2].startsWith("hysteria2://${"b".repeat(32)}@"))
        assertTrue("insecure=1&pinSHA256=${fingerprint.replace(":", "")}" in lines[2])
        assertFalse("pinSHA256=$fingerprint" in lines[2])
        assertTrue(lines.all { URI(it).fragment?.startsWith("OwnBridge-") == true })
    }

    @Test fun partialExportContainsOnlyReadyNodesAndRejectsDuplicates() {
        val ss = DeployedNode(PlannedNode(ipv4, ProxyProtocol.SHADOWSOCKS, 28443),
            credential(ProxyProtocol.SHADOWSOCKS))
        assertEquals(1, CompositeV2RayExport(listOf(ss)).shareLinks.trim().lines().size)
        assertThrows(IllegalArgumentException::class.java) { CompositeV2RayExport(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CompositeV2RayExport(listOf(ss, ss)) }
    }

    private fun credential(protocol: ProxyProtocol): ProxyCredential = when (protocol) {
        ProxyProtocol.SHADOWSOCKS -> ProxyCredential.Shadowsocks("a".repeat(32))
        ProxyProtocol.VLESS -> ProxyCredential.VlessReality(
            "bf000d23-0752-40b4-affe-68f7707a9661", "B".repeat(43),
            "0123456789abcdef", "www.cloudflare.com")
        ProxyProtocol.HYSTERIA2 -> ProxyCredential.Hysteria2("b".repeat(32), fingerprint)
    }
}
