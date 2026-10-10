package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompositePlanManifestTest {
    @Test fun manifestIsStableAsciiAndContainsOnlySelectedNodes() {
        val plan = CompositeDeploymentPlan(
            sshHost = "example.invalid",
            os = "Ubuntu 26.04",
            nodes = listOf(
                PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4),
                    ProxyProtocol.SHADOWSOCKS, 28443),
                PlannedNode(TargetAddress("203.0.113.10", AddressFamily.IPV4),
                    ProxyProtocol.HYSTERIA2, 28444),
                PlannedNode(TargetAddress("2001:db8::10", AddressFamily.IPV6),
                    ProxyProtocol.SHADOWSOCKS, 28445),
                PlannedNode(TargetAddress("2001:db8::10", AddressFamily.IPV6),
                    ProxyProtocol.HYSTERIA2, 28446),
            ),
        )
        assertEquals(
            "ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\n" +
                "ipv4 203.0.113.10 hy2 28444\n" +
                "ipv6 2001:db8::10 ss 28445\n" +
                "ipv6 2001:db8::10 hy2 28446\n",
            CompositePlanManifest.bytes(plan).toString(Charsets.US_ASCII),
        )
        assertEquals(64, CompositePlanManifest.sha256(plan).length)
        assertEquals(plan, CompositePlanManifest.parse(plan.sshHost, plan.os,
            CompositePlanManifest.bytes(plan)))
    }

    @Test fun savedManifestRejectsTamperingAndNonCanonicalInput() {
        val valid = "ownbridge-plan-v2\nipv4 203.0.113.10 ss 28443\n"
        listOf(
            valid.dropLast(1),
            valid + "\n",
            valid.replace("28443", "028443"),
            valid.replace(" ss ", " ssh "),
            valid + "ipv4 203.0.113.10 ss 28443\n",
        ).forEach { invalid ->
            assertThrows(RuntimeException::class.java) {
                CompositePlanManifest.parse("203.0.113.10", "Ubuntu 26.04",
                    invalid.toByteArray(Charsets.US_ASCII))
            }
        }
    }
}
