package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryParserTest {
    @Test
    fun parsesAddressesWithoutInventingMissingFamilies() {
        val output = """
            2: eth0    inet 203.0.113.10/24 brd 203.0.113.255 scope global eth0
            2: eth0    inet6 2001:db8::10/64 scope global
            1: lo      inet 127.0.0.1/8 scope host lo
        """.trimIndent()

        val (ipv4, ipv6) = InventoryParser.addresses(output)

        assertEquals(listOf("203.0.113.10"), ipv4)
        assertEquals(listOf("2001:db8::10"), ipv6)
    }

    @Test
    fun parsesListeningPortsAndProcesses() {
        val output = """
            tcp LISTEN 0 128 0.0.0.0:22 0.0.0.0:* users:(("sshd",pid=123,fd=3))
            udp UNCONN 0 0 [::]:443 [::]:* users:(("service",pid=456,fd=4))
        """.trimIndent()

        assertEquals(
            listOf(
                ListeningPort("tcp", "0.0.0.0", 22, "sshd"),
                ListeningPort("udp", "::", 443, "service"),
            ),
            InventoryParser.ports(output),
        )
    }

    @Test
    fun parsesSystemAndRunningServices() {
        assertEquals("Ubuntu 24.04 LTS", InventoryParser.system("PRETTY_NAME=\"Ubuntu 24.04 LTS\""))
        assertEquals(
            listOf("ssh.service", "systemd-journald.service"),
            InventoryParser.services("ssh.service loaded active running\nsystemd-journald.service loaded active running"),
        )
    }
}
