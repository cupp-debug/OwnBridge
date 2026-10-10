package app.ownbridge.core

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ShadowsocksExportTest {
    private val plan = ShadowsocksPlan("203.0.113.10", "203.0.113.10", 28443, "Ubuntu 26.04")

    @Test fun emitsMihomoNodeAndUri() {
        val export = ShadowsocksExport(plan, "abcdefghijklmnopqrstuvwxyzABCDEFG")
        assertTrue(export.clashYaml.contains("server: 203.0.113.10"))
        assertTrue(export.clashYaml.contains("port: 28443"))
        assertTrue(export.nodeUri.startsWith("ss://"))
    }

    @Test fun rejectsUnexpectedSecretCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            ShadowsocksExport(plan, "malicious\npassword: exposed" )
        }
    }
}
