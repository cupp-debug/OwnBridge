package app.ownbridge.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class CompositeClashExportIntegrationTest {
    @Test fun locallyInstalledMihomoAcceptsThreeProtocolDualStackProfile() {
        val binary = System.getenv("OWNBRIDGE_MIHOMO_BINARY")?.let(Path::of)
        assumeTrue(binary != null && Files.isRegularFile(binary))
        val targets = listOf(
            TargetAddress("203.0.113.10", AddressFamily.IPV4),
            TargetAddress("2001:db8::10", AddressFamily.IPV6),
        )
        val nodes = targets.flatMapIndexed { addressIndex, address ->
            ProxyProtocol.entries.mapIndexed { protocolIndex, protocol ->
                val credential = when (protocol) {
                    ProxyProtocol.SHADOWSOCKS -> ProxyCredential.Shadowsocks("a".repeat(32))
                    ProxyProtocol.VLESS -> ProxyCredential.VlessReality(
                        "bf000d23-0752-40b4-affe-68f7707a9661", "B".repeat(43),
                        "0123456789abcdef", "www.cloudflare.com",
                    )
                    ProxyProtocol.HYSTERIA2 -> ProxyCredential.Hysteria2(
                        "b".repeat(32), (1..32).joinToString(":") { "AB" },
                    )
                }
                DeployedNode(PlannedNode(address, protocol, 28443 + addressIndex * 3 + protocolIndex),
                    credential)
            }
        }
        val directory = Files.createTempDirectory("ownbridge-mihomo-check-")
        try {
            val config = directory.resolve("profile.yaml")
            Files.writeString(config, CompositeClashExport(nodes).clashYaml)
            val process = ProcessBuilder(binary.toString(), "-t", "-f", config.toString(),
                "-d", directory.toString()).redirectErrorStream(true).start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            assumeTrue("Mihomo validation timed out", finished)
            val output = process.inputStream.bufferedReader().use { it.readText().takeLast(1_000) }
            assertEquals(output, 0, process.exitValue())
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
