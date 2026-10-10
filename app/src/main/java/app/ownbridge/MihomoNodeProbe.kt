package app.ownbridge

import android.content.Context
import android.os.Build
import app.ownbridge.core.CompositeClashExport
import app.ownbridge.core.NamedDeployedNode
import io.github.oviron.libmihomo.Clash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/** Tests the exported proxy itself. No TUN, VpnService, controller port, or local proxy listener. */
internal class MihomoNodeProbe(context: Context) {
    private val app = context.applicationContext
    private val lock = Mutex()
    private val home = File(app.noBackupFilesDir, "mihomo-node-probe")
    private val profile = File(home, "config.yaml")

    suspend fun test(
        export: CompositeClashExport,
        nodes: List<NamedDeployedNode> = export.namedNodes,
        onStarting: suspend (NamedDeployedNode) -> Unit,
        onResult: suspend (NamedDeployedNode, Int?) -> Unit,
    ) = lock.withLock {
        withContext(Dispatchers.IO) {
            check(home.isDirectory || home.mkdirs())
            Clash.load(app.applicationInfo.nativeLibraryDir)
            Clash.assertReady()
            check(Clash.bridgeABI() == Clash.EXPECTED_BRIDGE_ABI)
            profile.writeText(TEST_PROFILE_HEADER + export.clashYaml, Charsets.UTF_8)
            try {
                val init = JSONObject()
                    .put("home-dir", home.absolutePath)
                    .put("version", Build.VERSION.SDK_INT)
                val setup = withTimeout(15_000) { awaitQuickSetup(init.toString()) }
                check(setup.isEmpty()) { "Mihomo rejected the generated profile" }
                for (node in nodes) {
                    onStarting(node)
                    val data = JSONObject()
                        .put("proxy-name", node.name)
                        .put("test-url", TEST_URL)
                        .put("timeout", TEST_TIMEOUT_MS)
                    val result = try {
                        withTimeout(TEST_TIMEOUT_MS + 2_000L) {
                            awaitAction("testDelay", data.toString())
                        }
                    } catch (_: TimeoutCancellationException) {
                        null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    val delay = result?.takeIf { it.actionCode() == 0 }
                        ?.actionDataInt()?.takeIf { it > 0 }
                    onResult(node, delay)
                }
            } finally {
                // The Go runtime remains loaded, but no secret profile or listener remains active.
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        profile.writeText(EMPTY_PROFILE, Charsets.UTF_8)
                        try {
                            val cleared = withTimeout(8_000) { awaitAction("setupConfig", "{}") }
                            check(cleared.actionCode() == 0 && cleared.actionDataString() == "")
                        } finally {
                            val stopped = withTimeout(8_000) { awaitAction("stopListener", null) }
                            check(stopped.actionCode() == 0)
                        }
                    } finally {
                        profile.delete()
                    }
                }
            }
        }
    }

    private suspend fun awaitQuickSetup(init: String): String = suspendCancellableCoroutine { continuation ->
        Clash.quickSetup(init, "{}") { reply ->
            if (continuation.isActive) continuation.resume(reply ?: "No setup response")
        }
    }

    private suspend fun awaitAction(method: String, data: String?): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val action = JSONObject().put("id", "ownbridge-probe").put("method", method)
            if (data != null) action.put("data", data)
            Clash.invokeAction(action.toString()) { reply ->
                if (continuation.isActive) {
                    val parsed = runCatching { JSONObject(reply ?: "") }.getOrDefault(JSONObject())
                    continuation.resume(parsed)
                }
            }
        }

    private fun JSONObject.actionCode(): Int =
        if (has("code")) optInt("code", -1) else optInt("Code", -1)

    private fun JSONObject.actionDataInt(): Int =
        if (has("data")) optInt("data", -1) else optInt("Data", -1)

    private fun JSONObject.actionDataString(): String =
        if (has("data")) optString("data") else optString("Data")

    companion object {
        const val TEST_URL = "https://www.gstatic.com/generate_204"
        private const val TEST_TIMEOUT_MS = 5_000
        private const val TEST_PROFILE_HEADER = """
port: 0
socks-port: 0
mixed-port: 0
allow-lan: false
ipv6: true
unified-delay: true
log-level: error
external-controller: ''
tun:
  enable: false
"""
        private const val EMPTY_PROFILE = """
port: 0
socks-port: 0
mixed-port: 0
allow-lan: false
log-level: error
external-controller: ''
tun:
  enable: false
proxies: []
proxy-groups: []
rules: []
"""
    }
}
