package app.ownbridge

import app.ownbridge.core.DeploymentStep
import app.ownbridge.core.ShadowsocksPlan

internal enum class DeploymentEventStatus { RUNNING, PASSED, FAILED }

internal enum class DeploymentAction { IDENTITY, PACKAGE, UNPACK, STEP, EXTERNAL, PRIOR_ATTEMPT }

internal enum class DeploymentMessage { PACKAGE_VERIFIED, UNPACKED, PREVIOUSLY_COMPLETED, EXTERNAL_REACHED }

internal data class DeploymentEvent(
    val action: DeploymentAction,
    val step: DeploymentStep?,
    val command: String?,
    val source: String?,
    val result: String?,
    val message: DeploymentMessage?,
    val status: DeploymentEventStatus,
)

internal enum class DeploymentFailure {
    IDENTITY,
    AUTHENTICATION,
    NETWORK,
    TRANSFER,
    HASH_MISMATCH,
    UNPACK,
    REMOTE_STEP,
    EXTERNAL_REACHABILITY,
    STORAGE,
    UNKNOWN,
}

internal sealed interface DeploymentState {
    data object Idle : DeploymentState
    data class Running(
        val runId: String,
        val plan: ShadowsocksPlan,
        val events: List<DeploymentEvent>,
    ) : DeploymentState
    data class Failed(
        val runId: String,
        val plan: ShadowsocksPlan,
        val events: List<DeploymentEvent>,
        val reason: DeploymentFailure,
    ) : DeploymentState
    data class ServerReady(
        val runId: String,
        val plan: ShadowsocksPlan,
        val events: List<DeploymentEvent>,
        val secret: String,
    ) : DeploymentState
}

internal val DeploymentStep.title: String
    get() = when (this) {
        DeploymentStep.PREFLIGHT -> "preflight.py"
        DeploymentStep.FETCH -> "fetch.sh"
        DeploymentStep.CONFIGURE -> "configure.py"
        DeploymentStep.ACTIVATE -> "activate.sh"
        DeploymentStep.VERIFY -> "verify.sh"
    }
