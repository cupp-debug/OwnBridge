package app.ownbridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** Read-only completed result, independent of the active deployment recovery pointer. */
internal class CompletedRunSession {
    var compositeSnapshot: CompositeDeploymentState.Settled? by mutableStateOf(null)
        private set
    var legacySnapshot: DeploymentState.ServerReady? by mutableStateOf(null)
        private set
    var deliveryPassword: String? by mutableStateOf(null)
        private set
    var savingRunId: String? by mutableStateOf(null)
        private set
    var saveErrorRunId: String? by mutableStateOf(null)
        private set
    private var autoSaveAttemptedRunId: String? = null

    fun markAutoSaveAttempt(runId: String): Boolean {
        if (autoSaveAttemptedRunId == runId) return false
        autoSaveAttemptedRunId = runId
        return true
    }

    suspend fun finish(
        runId: String,
        composite: CompositeDeploymentState.Settled?,
        legacy: DeploymentState.ServerReady?,
        password: String,
        request: SubscriptionRequest?,
        delivery: SubscriptionDeliveryState,
        history: HistoryController,
        compositeDeployment: CompositeDeploymentController,
        deployment: DeploymentController,
    ): Boolean {
        if (savingRunId != null || composite == null && legacy == null) return false
        savingRunId = runId
        saveErrorRunId = null
        return try {
            val released = persistCompletedRun(runId, composite, legacy, password,
                request, delivery, history, compositeDeployment, deployment)
            if (released) {
                compositeSnapshot = composite
                legacySnapshot = legacy
                deliveryPassword = password.takeUnless { delivery is SubscriptionDeliveryState.Ready }
            } else saveErrorRunId = runId
            released
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            saveErrorRunId = runId
            false
        } finally {
            savingRunId = null
        }
    }

    fun clearDeliveryPassword() { deliveryPassword = null }

    fun clear() {
        compositeSnapshot = null
        legacySnapshot = null
        deliveryPassword = null
        autoSaveAttemptedRunId = null
        saveErrorRunId = null
    }
}
