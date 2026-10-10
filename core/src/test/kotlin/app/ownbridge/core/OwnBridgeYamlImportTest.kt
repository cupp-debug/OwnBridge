package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OwnBridgeYamlImportTest {
    private val ipv4 = TargetAddress("203.0.113.10", AddressFamily.IPV4)
    private val ipv6 = TargetAddress("2001:db8::10", AddressFamily.IPV6)
    private val pin = (1..32).joinToString(":") { "AB" }
    private val sample = CompositeClashExport(listOf(
        DeployedNode(PlannedNode(ipv4, ProxyProtocol.SHADOWSOCKS, 28443),
            ProxyCredential.Shadowsocks("a".repeat(32))),
        DeployedNode(PlannedNode(ipv4, ProxyProtocol.VLESS, 28444),
            ProxyCredential.VlessReality("bf000d23-0752-40b4-affe-68f7707a9661",
                "B".repeat(43), "0123456789abcdef", "www.cloudflare.com")),
        DeployedNode(PlannedNode(ipv6, ProxyProtocol.HYSTERIA2, 28445),
            ProxyCredential.Hysteria2("b".repeat(32), pin)),
    )).clashYaml

    @Test fun importsOwnExportForAllProtocolsAndBothAddressFamilies() {
        assertEquals(sample, OwnBridgeYamlImport.parse(sample).clashYaml)
        assertEquals(sample, OwnBridgeYamlImport.parse(sample.replace("\n", "\r\n")).clashYaml)
    }

    @Test fun rejectsInjectedMihomoSettingsAndAlteredNode() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnBridgeYamlImport.parse("mixed-port: 7890\n$sample")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnBridgeYamlImport.parse(sample + "tun:\n  enable: true\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnBridgeYamlImport.parse(sample.replace("OwnBridge-SS-IPv4-1", "Other-SS-IPv4-1"))
        }
    }

    @Test fun rejectsOversizedOrIncompleteProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnBridgeYamlImport.parse("x".repeat(65_537))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OwnBridgeYamlImport.parse(sample.substringBefore("proxy-groups:"))
        }
    }
}
