package app.ownbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ownbridge.core.AddressFamily
import app.ownbridge.core.CompositeClashExport
import app.ownbridge.core.DeployedNode
import app.ownbridge.core.PlannedNode
import app.ownbridge.core.ProxyCredential
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.TargetAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MihomoNodeProbeInstrumentedTest {
    @Test fun loadsGeneratedProfileAndReportsUnreachableNodeWithoutVpn() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val node = DeployedNode(
            PlannedNode(TargetAddress("203.0.113.1", AddressFamily.IPV4), ProxyProtocol.SHADOWSOCKS, 28443),
            ProxyCredential.Shadowsocks("a".repeat(32)),
        )
        val observed = mutableListOf<Int?>()
        MihomoNodeProbe(context).test(
            CompositeClashExport(listOf(node)),
            onStarting = {},
            onResult = { _, delay -> observed += delay },
        )
        assertEquals(1, observed.size)
        assertNull(observed.single())
        assertFalse(File(context.noBackupFilesDir, "mihomo-node-probe/config.yaml").exists())
    }
}
