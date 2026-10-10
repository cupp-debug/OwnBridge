package app.ownbridge

import app.ownbridge.core.DigestMismatchException
import com.jcraft.jsch.JSchException

/** Keep uncertain SSH delivery distinct from a confirmed server-side command failure. */
internal object CompositeRunFailureClassifier {
    fun classify(error: Throwable, hostKeyChanged: Boolean, commandStarted: Boolean): CompositeRunFailure =
        when {
            hostKeyChanged -> CompositeRunFailure.IDENTITY
            error is DigestMismatchException -> CompositeRunFailure.HASH_MISMATCH
            error is JSchException && error.message?.contains("auth fail", ignoreCase = true) == true ->
                CompositeRunFailure.AUTHENTICATION
            error is JSchException -> if (commandStarted) CompositeRunFailure.INTERRUPTED
                else CompositeRunFailure.NETWORK
            error is SshCommandExecutor.CommandTimeoutException ||
                error is SshCommandExecutor.UnknownExitStatusException -> CompositeRunFailure.INTERRUPTED
            error is RemoteCommandFailed || error is SshCommandExecutor.OutputLimitException ->
                CompositeRunFailure.REMOTE
            else -> CompositeRunFailure.UNKNOWN
        }
}
