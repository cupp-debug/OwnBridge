package app.ownbridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** UI-facing state holder; stale attempts cannot overwrite a newer result. */
internal class DetectionController(
    private val scope: CoroutineScope,
    private val hostKeys: HostKeyStore,
    private val networkRoute: SshNetworkRouteInspector,
) {
    var state: DetectionUiState by mutableStateOf(DetectionUiState.Idle)
        private set

    private var runner: SshDetectionRunner? = null
    private var job: Job? = null
    private var generation = 0

    fun detect(host: String, username: String, password: String, savedRunId: String?) {
        stopActive()
        val attempt = ++generation
        val activeRunner = SshDetectionRunner()
        val route = networkRoute.current()
        runner = activeRunner
        state = DetectionUiState.Running(emptyList())
        job = scope.launch {
            try {
                val result = activeRunner.detect(
                    host = host,
                    username = username,
                    password = password,
                    networkRoute = route,
                    savedRunId = savedRunId,
                    trustedFingerprint = hostKeys.get(host),
                    pinFirstKey = { identity -> hostKeys.pin(host, identity.fingerprint) },
                ) { events ->
                    if (attempt == generation) state = DetectionUiState.Running(events)
                }
                if (attempt == generation) state = result
            } catch (_: CancellationException) {
                // User cancellation or input invalidation already updated the visible state.
            } finally {
                if (attempt == generation) runner = null
            }
        }
    }

    fun trustChangedHost(host: String, username: String, password: String, savedRunId: String?) {
        val failed = state as? DetectionUiState.Failed ?: return
        val change = failed.hostKeyChange ?: return
        if (failed.reason != DetectionFailure.HOST_KEY_CHANGED ||
            !change.matches(host, hostKeys.get(host))) {
            invalidate()
            return
        }
        if (!hostKeys.replace(host, change.previousFingerprint, change.observed.fingerprint)) {
            state = DetectionUiState.Failed(failed.events, DetectionFailure.STORAGE)
            return
        }
        detect(host, username, password, savedRunId)
    }

    fun cancel() {
        val events = (state as? DetectionUiState.Running)?.events
        stopActive()
        generation++
        state = if (events != null) {
            DetectionUiState.Cancelled(events.map { event ->
                if (event.status == DetectionStepStatus.RUNNING) {
                    event.copy(status = DetectionStepStatus.CANCELLED)
                } else event
            })
        } else DetectionUiState.Idle
    }

    fun invalidate() {
        stopActive()
        generation++
        state = DetectionUiState.Idle
    }

    private fun stopActive() {
        runner?.cancel()
        job?.cancel()
        runner = null
        job = null
    }
}
