package app.ownbridge

import app.ownbridge.core.DetectionStep
import app.ownbridge.core.SavedRunCheck
import app.ownbridge.core.SavedRunPresence
import app.ownbridge.core.ServerInventory

internal enum class DetectionStepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    CANCELLED,
}

/** A snapshot of the Android default route, not a claim about a remote SSH failure. */
internal enum class SshNetworkRoute { VPN, OTHER, UNKNOWN }

internal data class DetectionEvent(
    val step: DetectionStep,
    val command: String?,
    val result: String?,
    val exitCode: Int?,
    val durationMillis: Long?,
    val status: DetectionStepStatus,
    val networkRoute: SshNetworkRoute? = null,
    val savedRunPresence: SavedRunPresence? = null,
)

internal enum class DetectionFailure {
    INVALID_INPUT,
    NETWORK,
    DNS,
    REFUSED,
    UNREACHABLE,
    HANDSHAKE,
    HOST_KEY_CHANGED,
    AUTHENTICATION,
    COMMAND,
    TIMEOUT,
    UNKNOWN,
    STORAGE,
}

internal data class HostIdentity(
    val fingerprint: String,
    val algorithm: String,
)

/** The exact host key observed before authentication, awaiting explicit user confirmation. */
internal data class HostKeyChange(
    val host: String,
    val previousFingerprint: String,
    val observed: HostIdentity,
) {
    fun matches(host: String, savedFingerprint: String?): Boolean =
        this.host.trim().equals(host.trim(), ignoreCase = true) &&
            previousFingerprint == savedFingerprint
}

internal sealed interface DetectionUiState {
    data object Idle : DetectionUiState
    data class Running(val events: List<DetectionEvent>) : DetectionUiState
    data class Cancelled(val events: List<DetectionEvent>) : DetectionUiState
    data class Failed(
        val events: List<DetectionEvent>,
        val reason: DetectionFailure,
        val hostKeyChange: HostKeyChange? = null,
    ) : DetectionUiState
    data class Ready(
        val events: List<DetectionEvent>,
        val inventory: ServerInventory,
        val savedRunCheck: SavedRunCheck? = null,
    ) : DetectionUiState
}

/** A route observation is a warning, never a diagnosis of the SSH failure. */
internal fun DetectionUiState.showVpnWarning(): Boolean = when (this) {
    is DetectionUiState.Running -> events.firstOrNull()?.networkRoute == SshNetworkRoute.VPN
    is DetectionUiState.Failed -> events.firstOrNull()?.networkRoute == SshNetworkRoute.VPN
    else -> false
}
