package app.ownbridge

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ownbridge.core.HistoryDelivery
import app.ownbridge.core.HistoryKind
import app.ownbridge.core.HistoryOutcome
import app.ownbridge.core.HistoryPhase
import app.ownbridge.core.HistorySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Captures attempt details and serializes completed, saved snapshots on one IO actor. */
internal class HistoryController(context: Context, scope: CoroutineScope) {
    var records: List<HistorySnapshot> by mutableStateOf(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var storageError by mutableStateOf(false)
        private set
    var unreadableCount by mutableStateOf(0)
        private set

    private val store = HistoryEncryptedStore(context)
    private sealed interface Update {
        data class Capture(
            val change: (MutableMap<String, HistorySnapshot>) -> HistorySnapshot?,
        ) : Update
        data class Save(val id: String, val reply: CompletableDeferred<Boolean>) : Update
        data class Delete(val id: String, val reply: CompletableDeferred<Boolean>) : Update
        data class Archive(val id: String) : Update
    }
    private val updates = Channel<Update>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { store.load() }.getOrNull()
            if (loaded == null) {
                withContext(Dispatchers.Main) { storageError = true; loading = false }
                for (update in updates) when (update) {
                    is Update.Save -> update.reply.complete(false)
                    is Update.Delete -> update.reply.complete(false)
                    else -> Unit
                }
                return@launch
            }
            val current = loaded.snapshots.associateByTo(mutableMapOf()) { it.id }
            val saved = current.toMutableMap()
            withContext(Dispatchers.Main) {
                records = saved.values.sortedByDescending { it.updatedAt }
                unreadableCount = loaded.unreadable
                loading = false
            }
            for (update in updates) {
                try {
                    when (update) {
                        is Update.Capture -> update.change(current)?.let { snapshot ->
                            if (snapshot.id in saved) {
                                store.save(snapshot)
                                saved[snapshot.id] = snapshot
                                withContext(Dispatchers.Main) {
                                    records = saved.values.sortedByDescending { it.updatedAt }
                                }
                            }
                            current[snapshot.id] = snapshot
                        }
                        is Update.Save -> {
                            val snapshot = current[update.id]?.takeIf {
                                it.kind == HistoryKind.DEPLOYMENT && it.outcome == HistoryOutcome.SUCCEEDED
                            }
                            if (snapshot == null) {
                                update.reply.complete(false)
                                continue
                            }
                            store.save(snapshot)
                            saved[snapshot.id] = snapshot
                            withContext(Dispatchers.Main) {
                                records = saved.values.sortedByDescending { it.updatedAt }
                                storageError = false
                            }
                            update.reply.complete(true)
                        }
                        is Update.Archive -> {
                            val earlier = saved[update.id]
                            if (earlier == null) {
                                current.remove(update.id)
                                continue
                            }
                            val snapshot = earlier.copy(archived = true,
                                updatedAt = System.currentTimeMillis())
                            store.save(snapshot)
                            saved[snapshot.id] = snapshot
                            current[snapshot.id] = snapshot
                            withContext(Dispatchers.Main) {
                                records = saved.values.sortedByDescending { it.updatedAt }
                            }
                        }
                        is Update.Delete -> {
                            store.delete(update.id)
                            saved.remove(update.id)
                            current.remove(update.id)
                            withContext(Dispatchers.Main) {
                                records = saved.values.sortedByDescending { it.updatedAt }
                                storageError = false
                            }
                            update.reply.complete(true)
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { storageError = true }
                    when (update) {
                        is Update.Save -> update.reply.complete(false)
                        is Update.Delete -> update.reply.complete(false)
                        else -> Unit
                    }
                }
            }
        }
    }

    fun captureComposite(state: CompositeDeploymentState, password: String) {
        val now = System.currentTimeMillis()
        capture { current ->
            val id = when (state) {
                is CompositeDeploymentState.NeedsReconciliation -> state.record.runId
                is CompositeDeploymentState.Running -> state.record.runId
                is CompositeDeploymentState.Settled -> state.record.runId
                else -> return@capture null
            }
            HistoryCapture.composite(state, password, current[id], now)
        }
    }

    fun captureLegacy(state: DeploymentState, password: String) {
        val now = System.currentTimeMillis()
        capture { current ->
            val id = when (state) {
                is DeploymentState.Running -> state.runId
                is DeploymentState.Failed -> state.runId
                is DeploymentState.ServerReady -> state.runId
                DeploymentState.Idle -> return@capture null
            }
            HistoryCapture.legacy(state, password, current[id], now)
        }
    }

    fun captureDelivery(runId: String, host: String, yaml: String,
        state: SubscriptionDeliveryState) {
        if (state == SubscriptionDeliveryState.Idle) return
        val now = System.currentTimeMillis()
        capture { current ->
            val previous = current[runId] ?: HistorySnapshot(runId, HistoryKind.DEPLOYMENT,
                host, now, now, HistoryPhase.DEPLOYMENT, HistoryOutcome.SUCCEEDED,
                clashYaml = yaml)
            val last = previous.deliveryEvents.lastOrNull()
            val observed = when (state) {
                is SubscriptionDeliveryState.Publishing -> if (state.retryReason == null)
                    HistoryDelivery.PUBLISHING else HistoryDelivery.FAILED
                is SubscriptionDeliveryState.Ready -> HistoryDelivery.READY
                is SubscriptionDeliveryState.Failed -> HistoryDelivery.FAILED
                SubscriptionDeliveryState.Idle -> error("Handled above")
            }
            val failure = when (state) {
                is SubscriptionDeliveryState.Publishing -> state.retryReason?.name
                is SubscriptionDeliveryState.Failed -> state.reason.name
                else -> null
            }
            if (last?.state == observed && last.failure == failure &&
                (state !is SubscriptionDeliveryState.Ready || previous.subscriptionUrl == state.url)) {
                return@capture null
            }
            HistoryCapture.delivery(previous, yaml, state, now)
        }
    }

    suspend fun saveCompleted(runId: String): Boolean {
        val reply = CompletableDeferred<Boolean>()
        if (updates.trySend(Update.Save(runId, reply)).isFailure) {
            storageError = true
            return false
        }
        return reply.await()
    }

    suspend fun deleteRecord(id: String): Boolean {
        val reply = CompletableDeferred<Boolean>()
        if (updates.trySend(Update.Delete(id, reply)).isFailure) {
            storageError = true
            return false
        }
        return reply.await()
    }

    /** Archive a saved record; forget an unsaved in-memory draft. */
    fun finishRun(runId: String) {
        if (updates.trySend(Update.Archive(runId)).isFailure) storageError = true
    }

    private fun capture(change: (MutableMap<String, HistorySnapshot>) -> HistorySnapshot?) {
        if (updates.trySend(Update.Capture(change)).isFailure) storageError = true
    }
}
