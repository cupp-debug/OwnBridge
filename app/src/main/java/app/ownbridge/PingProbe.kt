package app.ownbridge

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal enum class PingFamily { IPV4, IPV6 }

internal data class PingSample(val latencyMs: Double?)

internal data class PingSummary(val samples: List<PingSample>) {
    val received: Int get() = samples.count { it.latencyMs != null }
    val lossPercent: Int get() = if (samples.isEmpty()) 0 else (samples.size - received) * 100 / samples.size
    val averageMs: Double? get() = samples.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()
}

/** Uses the device's ICMP utility, never a TCP fallback or a remote SSH command. */
internal class PingProbe {
    private val activeProcess = AtomicReference<Process?>()

    fun supportedCommand(family: PingFamily): List<String>? {
        val target = if (family == PingFamily.IPV4) "127.0.0.1" else "::1"
        return candidates(family).firstOrNull { command ->
            try {
                execute(command + listOf("-n", "-c", "1", "-W", "1", target), 3).latencyMs != null
            } catch (_: Exception) {
                false
            }
        }
    }

    fun resolve(host: String, supported: Set<PingFamily>): InetAddress? {
        val input = host.trim().removeSurrounding("[", "]")
        if (input.isEmpty() || input.any { it.isWhitespace() || it == '/' || it == '@' }) return null
        return InetAddress.getAllByName(input).firstOrNull { address ->
            (address is Inet4Address && PingFamily.IPV4 in supported) ||
                (address is Inet6Address && PingFamily.IPV6 in supported)
        }
    }

    fun once(command: List<String>, address: InetAddress): PingSample =
        execute(command + listOf("-n", "-c", "1", "-W", "2", address.hostAddress), 4)

    fun cancel() {
        activeProcess.getAndSet(null)?.destroyForcibly()
    }

    private fun execute(arguments: List<String>, timeoutSeconds: Long): PingSample {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        activeProcess.set(process)
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return PingSample(null)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() > 1) throw IllegalStateException("Ping command failed: ${process.exitValue()}")
            return PingSample(PingOutput.latencyMs(output))
        } finally {
            activeProcess.compareAndSet(process, null)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun candidates(family: PingFamily): List<List<String>> = when (family) {
        PingFamily.IPV4 -> listOf(listOf("/system/bin/ping"))
        PingFamily.IPV6 -> listOf(listOf("/system/bin/ping", "-6"), listOf("/system/bin/ping6"))
    }
}

internal object PingOutput {
    private val time = Regex("\\btime\\s*([=<])\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms", RegexOption.IGNORE_CASE)

    fun latencyMs(output: String): Double? = time.find(output)?.let { match ->
        val value = match.groupValues[2].toDoubleOrNull() ?: return@let null
        if (match.groupValues[1] == "<") value / 2 else value
    }
}
