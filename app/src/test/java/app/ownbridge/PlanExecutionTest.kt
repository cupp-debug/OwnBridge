package app.ownbridge

import app.ownbridge.core.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanExecutionTest {
    @Test fun visibleProtocolChoicesMapToTheCompositeModulesWithoutDroppingSelections() {
        val selection = PlanSelection(
            "203.0.113.10", listOf("203.0.113.10"),
            listOf("VLESS", "SS", "HY2"), listOf("Clash / Mihomo"),
        )
        assertEquals(listOf(ProxyProtocol.VLESS, ProxyProtocol.SHADOWSOCKS,
            ProxyProtocol.HYSTERIA2), selection.compositeProtocols())
        assertEquals(3, selection.possibleNodeCount)
    }
}
