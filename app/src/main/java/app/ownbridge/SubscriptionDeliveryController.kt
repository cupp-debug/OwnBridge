package app.ownbridge

import android.content.res.AssetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ownbridge.core.VerifiedTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface SubscriptionDeliveryState {
    data object Idle : SubscriptionDeliveryState
    data class Publishing(
        val attempt: Int,
        val retryReason: SubscriptionFailure? = null,
    ) : SubscriptionDeliveryState
    data class Ready(val url: String) : SubscriptionDeliveryState
    data class Failed(val reason: SubscriptionFailure) : SubscriptionDeliveryState
}

/** Bound automatic retries to transient post-SSH publication failures. */
internal object SubscriptionRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun shouldRetry(reason: SubscriptionFailure, attempt: Int): Boolean =
        attempt < MAX_ATTEMPTS && reason in setOf(
            SubscriptionFailure.REMOTE, SubscriptionFailure.RETRIEVAL)

    fun delayAfter(attempt: Int): Long = attempt * 1_000L

    suspend fun <T> execute(
        publish: suspend () -> T,
        onAttempt: (Int) -> Unit,
        onRetryableFailure: (Int, SubscriptionFailure) -> Unit,
        wait: suspend (Long) -> Unit = ::delay,
    ): T {
        for (attempt in 1..MAX_ATTEMPTS) {
            onAttempt(attempt)
            val failure = try {
                return publish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SubscriptionPublishException) {
                error.reason
            } catch (_: Exception) {
                SubscriptionFailure.REMOTE
            }
            if (!shouldRetry(failure, attempt)) throw SubscriptionPublishException(failure)
            onRetryableFailure(attempt, failure)
            wait(delayAfter(attempt))
        }
        error("The final attempt must return or throw")
    }
}

/** A delivery failure never changes the already verified deployment result. */
internal class SubscriptionDeliveryController(
    private val scope: CoroutineScope,
    private val hostKeys: HostKeyStore,
    assets: AssetManager,
) {
    var state: SubscriptionDeliveryState by mutableStateOf(SubscriptionDeliveryState.Idle)
        private set

    private val publisher = SubscriptionSshPublisher(assets)
    private var selectedProfile: Pair<String, String>? = null
    private var job: Job? = null
    private var generation = 0

    fun selectRun(runId: String?, yaml: String? = null) {
        val profile = if (runId != null && yaml != null) {
            runId to VerifiedTransfer.sha256(yaml.toByteArray(Charsets.UTF_8))
        } else null
        if (selectedProfile == profile) return
        job?.cancel()
        publisher.cancel()
        job = null
        selectedProfile = profile
        generation++
        state = SubscriptionDeliveryState.Idle
    }

    fun request(
        runId: String, sshHost: String, publicHost: String,
        username: String, password: String, yaml: String,
    ) {
        selectRun(runId, yaml)
        if (state is SubscriptionDeliveryState.Publishing ||
            state is SubscriptionDeliveryState.Ready) return
        if (password.isBlank()) {
            state = SubscriptionDeliveryState.Failed(SubscriptionFailure.CREDENTIALS)
            return
        }
        val fingerprint = hostKeys.get(sshHost)
        if (fingerprint.isNullOrBlank()) {
            state = SubscriptionDeliveryState.Failed(SubscriptionFailure.IDENTITY)
            return
        }
        state = SubscriptionDeliveryState.Publishing(1)
        val requestGeneration = ++generation
        job = scope.launch {
            try {
                val url = SubscriptionRetryPolicy.execute(
                    publish = { publisher.publish(runId, sshHost, publicHost, username,
                        password, fingerprint, yaml) },
                    onAttempt = { attempt ->
                        if (requestGeneration == generation) {
                            state = SubscriptionDeliveryState.Publishing(attempt)
                        }
                    },
                    onRetryableFailure = { attempt, failure ->
                        if (requestGeneration == generation) {
                            state = SubscriptionDeliveryState.Publishing(attempt, failure)
                        }
                    },
                )
                if (requestGeneration == generation) state = SubscriptionDeliveryState.Ready(url)
            } catch (_: CancellationException) {
                // A remote publish may have completed; a later request reuses its token.
            } catch (error: SubscriptionPublishException) {
                if (requestGeneration == generation) state = SubscriptionDeliveryState.Failed(error.reason)
            } finally {
                if (requestGeneration == generation) job = null
            }
        }
    }

    fun dispose() {
        job?.cancel()
        publisher.cancel()
        job = null
        generation++
    }
}
