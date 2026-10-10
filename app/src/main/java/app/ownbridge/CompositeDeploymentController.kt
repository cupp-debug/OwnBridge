package app.ownbridge

import android.content.res.AssetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositeProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

internal sealed interface CompositeDeploymentState {
    data object Idle : CompositeDeploymentState
    data object LocalRecordCorrupt : CompositeDeploymentState
    data class NeedsReconciliation(val record: CompositeDeploymentRecord) : CompositeDeploymentState
    data class Running(
        val record: CompositeDeploymentRecord,
        val events: List<CompositeRunEvent>,
    ) : CompositeDeploymentState
    data class Settled(
        val record: CompositeDeploymentRecord,
        val result: CompositeRunResult,
    ) : CompositeDeploymentState
}

/** Preview controller retains one active run and non-secret archived task snapshots. */
internal class CompositeDeploymentController(
    private val scope: CoroutineScope,
    private val hostKeys: HostKeyStore,
    private val records: CompositeDeploymentRecordStore,
    private val assets: AssetManager,
) {
    var state: CompositeDeploymentState by mutableStateOf(
        when (val saved = records.loadState()) {
            CompositeStoredRecord.Missing -> CompositeDeploymentState.Idle
            CompositeStoredRecord.Corrupt -> CompositeDeploymentState.LocalRecordCorrupt
            is CompositeStoredRecord.Valid -> CompositeDeploymentState.NeedsReconciliation(saved.record)
        },
    )
        private set

    private var runner: CompositeSshDeploymentRunner? = null
    private var job: Job? = null
    private var generation = 0

    /** A changed plan may replace the active pointer only after explicit plan confirmation. */
    fun start(
        plan: CompositeDeploymentPlan,
        username: String,
        password: String,
        allowNewTask: Boolean = false,
    ): Boolean {
        if (state is CompositeDeploymentState.Running) return false
        val existing = when (val saved = records.loadState()) {
            CompositeStoredRecord.Missing -> null
            CompositeStoredRecord.Corrupt -> return false
            is CompositeStoredRecord.Valid -> saved.record
        }
        val replacing = existing != null && existing.plan != plan
        if (replacing && !allowNewTask) return false
        val record = existing?.takeUnless { replacing } ?: CompositeDeploymentRecord(
            UUID.randomUUID().toString().replace("-", ""), plan,
            CompositeDeploymentPackageCatalog.VERSION,
        )
        val stored = runCatching {
            if (replacing) records.replaceActive(requireNotNull(existing), record)
            else records.save(record)
        }.getOrDefault(false)
        if (!stored) return false
        val priorEvents = (state as? CompositeDeploymentState.Settled)
            ?.takeIf { it.record == record }?.result?.events.orEmpty()
        val history = AttemptHistory.composite(priorEvents)
        stopActive()
        val attempt = ++generation
        val current = CompositeSshDeploymentRunner()
        val catalog = CompositeDeploymentPackageCatalog(assets)
        runner = current
        state = CompositeDeploymentState.Running(record, history)
        job = scope.launch {
            try {
                val result = current.run(
                    plan, record.runId, username, password, hostKeys.get(plan.sshHost),
                    catalog, existing != null && !replacing, record.bundleVersion != catalog.version,
                ) { events ->
                    if (attempt == generation) state = CompositeDeploymentState.Running(record, events + history)
                }
                if (attempt == generation) state = CompositeDeploymentState.Settled(
                    record, result.copy(events = result.events + history))
            } catch (_: CancellationException) {
                if (attempt == generation) state = CompositeDeploymentState.NeedsReconciliation(record)
            } catch (_: Exception) {
                if (attempt == generation) {
                    val events = (state as? CompositeDeploymentState.Running)?.events.orEmpty()
                    state = CompositeDeploymentState.Settled(
                        record, CompositeRunResult(null, emptyList(), events, CompositeRunFailure.UNKNOWN),
                    )
                }
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

    /** An archived task keeps its remote services and diagnostic journal untouched. */
    fun archiveCompleted(): Boolean {
        val settled = state as? CompositeDeploymentState.Settled ?: return false
        val journal = settled.result.journal ?: return false
        if (journal.overall(settled.record.plan) != CompositeProgress.ALL_SERVER_READY ||
            settled.result.nodes.size != settled.record.plan.nodes.size) return false
        if (!records.archiveCompleted(settled.record)) return false
        stopActive()
        generation++
        state = CompositeDeploymentState.Idle
        return true
    }

    fun archived(): List<CompositeDeploymentRecord> = records.archived()

    fun archiveMissingRemote(host: String, runId: String): Boolean {
        if (state is CompositeDeploymentState.Running) return false
        val record = (records.loadState() as? CompositeStoredRecord.Valid)?.record ?: return false
        if (!record.plan.sshHost.equals(host.trim(), ignoreCase = true) || record.runId != runId) return false
        if (!records.archiveMissingRemote(record)) {
            state = CompositeDeploymentState.LocalRecordCorrupt
            return false
        }
        stopActive()
        generation++
        state = CompositeDeploymentState.Idle
        return true
    }

    /** Leaves server diagnostics intact; permitted only before any protocol step starts. */
    fun discardUnstarted(): Boolean {
        val settled = state as? CompositeDeploymentState.Settled ?: return false
        val journal = settled.result.journal ?: return false
        if (!journal.noProtocolWorkStarted(settled.record.plan)) return false
        if (!records.clear()) return false
        stopActive()
        generation++
        state = CompositeDeploymentState.Idle
        return true
    }

    private fun stopActive() {
        runner?.cancel()
        job?.cancel()
        runner = null
        job = null
    }
}
