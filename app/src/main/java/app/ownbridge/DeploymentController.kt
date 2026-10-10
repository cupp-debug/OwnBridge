package app.ownbridge

import android.content.res.AssetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ownbridge.core.ShadowsocksPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

internal class DeploymentController(
    private val scope: CoroutineScope,
    private val hostKeys: HostKeyStore,
    private val records: DeploymentRecordStore,
    private val assets: AssetManager,
) {
    var state: DeploymentState by mutableStateOf(
        records.load()?.let { record ->
            DeploymentState.Failed(record.runId, record.plan, emptyList(), DeploymentFailure.UNKNOWN)
        } ?: DeploymentState.Idle,
    )
        private set

    private var runner: SshDeploymentRunner? = null
    private var job: Job? = null
    private var generation = 0

    fun start(
        plan: ShadowsocksPlan,
        username: String,
        password: String,
        allowNewTask: Boolean = false,
    ): Boolean {
        if (state is DeploymentState.Running) return false
        val existing = records.load()
        val replacing = existing != null && existing.plan != plan
        if (replacing && !allowNewTask) return false
        val runId = existing?.takeUnless { replacing }?.runId
            ?: UUID.randomUUID().toString().replace("-", "")
        val bundleVersion = existing?.takeUnless { replacing }?.bundleVersion
            ?: DeploymentPackageCatalog.CURRENT_VERSION
        val catalog = try {
            DeploymentPackageCatalog(assets, bundleVersion)
        } catch (_: IllegalArgumentException) {
            if (!replacing) state = DeploymentState.Failed(
                runId, plan, emptyList(), DeploymentFailure.UNKNOWN)
            return false
        }
        val record = DeploymentRecord(runId, plan, bundleVersion)
        val stored = if (replacing) records.replaceActive(requireNotNull(existing), record)
            else records.save(record)
        if (!stored) {
            if (!replacing) state = DeploymentState.Failed(
                runId, plan, emptyList(), DeploymentFailure.STORAGE)
            return false
        }
        val history = AttemptHistory.legacy((state as? DeploymentState.Failed)
            ?.takeIf { it.runId == runId }?.events.orEmpty())
        stopActive()
        val attempt = ++generation
        val currentRunner = SshDeploymentRunner()
        runner = currentRunner
        state = DeploymentState.Running(runId, plan, history)
        job = scope.launch {
            try {
                val result = currentRunner.run(
                    plan = plan,
                    runId = runId,
                    username = username,
                    password = password,
                    trustedFingerprint = hostKeys.get(plan.sshHost),
                    catalog = catalog,
                ) { events ->
                    if (attempt == generation) state = DeploymentState.Running(runId, plan, events + history)
                }
                if (attempt == generation) state = when (result) {
                    is DeploymentState.Failed -> result.copy(events = result.events + history)
                    is DeploymentState.ServerReady -> result.copy(events = result.events + history)
                    else -> result
                }
            } catch (_: CancellationException) {
                // A disconnected command may still be running remotely; its journal is authoritative.
            } finally {
                if (attempt == generation) runner = null
            }
        }
        return true
    }

    fun dispose() {
        stopActive()
        generation++
    }

    /** A completed and saved result may release its local recovery pointer. */
    fun finishCompleted(): Boolean {
        val ready = state as? DeploymentState.ServerReady ?: return false
        val record = records.load()?.takeIf { it.runId == ready.runId && it.plan == ready.plan }
            ?: return false
        if (!records.release(record)) return false
        stopActive()
        generation++
        state = DeploymentState.Idle
        return true
    }

    private fun stopActive() {
        runner?.cancel()
        job?.cancel()
        runner = null
        job = null
    }
}
