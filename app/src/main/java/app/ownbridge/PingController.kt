package app.ownbridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.Inet4Address
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class PingAvailability { HIDDEN, CHECKING, SUPPORTED, UNSUPPORTED }
internal enum class PingRunStatus { IDLE, RUNNING, COMPLETED, CANCELLED, FAILED }
internal enum class PingFailure { ADDRESS, COMMAND }

internal data class PingUiState(
    val availability: PingAvailability = PingAvailability.HIDDEN,
    val runStatus: PingRunStatus = PingRunStatus.IDLE,
    val target: String? = null,
    val command: String? = null,
    val samples: List<PingSample> = emptyList(),
    val failure: PingFailure? = null,
) {
    val hasLog: Boolean get() = runStatus != PingRunStatus.IDLE
    val summary: PingSummary get() = PingSummary(samples)
}

/** Input invalidation and cancellation are generation-guarded, like SSH detection. */
internal class PingController(private val scope: CoroutineScope) {
    var state: PingUiState by mutableStateOf(PingUiState())
        private set

    private var probe = PingProbe()
    private var commands: Map<PingFamily, List<String>> = emptyMap()
    private var checkedHost: String? = null
    private var job: Job? = null
    private var generation = 0

    fun invalidate() {
        stop()
        generation++
        commands = emptyMap()
        checkedHost = null
        state = PingUiState()
    }

    fun checkCapability(host: String) {
        if (host.isBlank()) { invalidate(); return }
        if (host == checkedHost && state.availability in setOf(PingAvailability.SUPPORTED, PingAvailability.UNSUPPORTED)) return
        stop()
        val attempt = ++generation
        val activeProbe = probe
        state = PingUiState(availability = PingAvailability.CHECKING)
        job = scope.launch {
            try {
                val families = candidateFamilies(host)
                val available = withContext(Dispatchers.IO) {
                    buildMap {
                        families.forEach { family ->
                            currentCoroutineContext().ensureActive()
                            activeProbe.supportedCommand(family)?.let { put(family, it) }
                        }
                    }
                }
                if (attempt == generation) {
                    commands = available
                    checkedHost = host
                    state = PingUiState(
                        availability = if (available.isNotEmpty())
                            PingAvailability.SUPPORTED else PingAvailability.UNSUPPORTED,
                    )
                }
            } catch (_: CancellationException) {
                // A newer address owns the visible state.
            }
        }
    }

    fun start(host: String) {
        if (state.availability != PingAvailability.SUPPORTED) return
        stop()
        val attempt = ++generation
        val activeProbe = probe
        state = PingUiState(availability = PingAvailability.SUPPORTED, runStatus = PingRunStatus.RUNNING)
        job = scope.launch {
            try {
                val address = withContext(Dispatchers.IO) { activeProbe.resolve(host, commands.keys) }
                if (attempt != generation) return@launch
                if (address == null) {
                    state = state.copy(runStatus = PingRunStatus.FAILED, failure = PingFailure.ADDRESS)
                    return@launch
                }
                val family = if (address is Inet4Address) PingFamily.IPV4 else PingFamily.IPV6
                val command = commands[family] ?: error("Ping command unavailable")
                state = state.copy(
                    target = address.hostAddress,
                    command = (command + listOf("-n", "-c", "1", "-W", "2", address.hostAddress)).joinToString(" "),
                )
                repeat(20) { index ->
                    val sample = withContext(Dispatchers.IO) { activeProbe.once(command, address) }
                    if (attempt != generation) return@launch
                    state = state.copy(samples = state.samples + sample)
                    if (index < 19) delay(1_000)
                }
                state = state.copy(runStatus = PingRunStatus.COMPLETED)
            } catch (_: CancellationException) {
                // cancel() or invalidate() already updated the state.
            } catch (_: Exception) {
                if (attempt == generation) state = state.copy(runStatus = PingRunStatus.FAILED, failure = PingFailure.COMMAND)
            }
        }
    }

    fun cancel() {
        if (state.runStatus != PingRunStatus.RUNNING) return
        stop()
        generation++
        state = state.copy(runStatus = PingRunStatus.CANCELLED)
    }

    /** Drop an old Ping log without repeating the local capability check. */
    fun clearRun() {
        if (!state.hasLog) return
        stop()
        generation++
        state = PingUiState(availability = state.availability)
    }

    fun dispose() = stop()

    private fun stop() {
        probe.cancel()
        job?.cancel()
        job = null
        probe = PingProbe()
    }

    private fun candidateFamilies(host: String): List<PingFamily> {
        val value = host.trim().removeSurrounding("[", "]")
        return when {
            ':' in value -> listOf(PingFamily.IPV6)
            value.matches(Regex("[0-9.]+")) -> listOf(PingFamily.IPV4)
            else -> listOf(PingFamily.IPV4, PingFamily.IPV6)
        }
    }
}
