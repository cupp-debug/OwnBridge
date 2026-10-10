package app.ownbridge

import com.jcraft.jsch.JSchException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/** Converts unstable library messages into bounded, non-secret user-facing categories. */
internal object SshDetectionFailureClassifier {
    fun classify(error: Throwable, hostKeyChanged: Boolean, storageFailed: Boolean): DetectionFailure {
        if (hostKeyChanged) return DetectionFailure.HOST_KEY_CHANGED
        if (storageFailed) return DetectionFailure.STORAGE
        if (error is SshCommandExecutor.CommandTimeoutException) return DetectionFailure.TIMEOUT
        if (error is SshCommandExecutor.OutputLimitException) return DetectionFailure.COMMAND

        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val messages = causes.mapNotNull { it.message?.lowercase(Locale.ROOT) }
        fun mentions(vararg tokens: String) = messages.any { message -> tokens.any(message::contains) }

        return when {
            mentions("auth fail", "auth cancel", "too many authentication failures") ->
                DetectionFailure.AUTHENTICATION
            causes.any { it is UnknownHostException } || mentions("unknownhostexception") ->
                DetectionFailure.DNS
            causes.any { it is NoRouteToHostException } || mentions("network is unreachable", "no route to host") ->
                DetectionFailure.UNREACHABLE
            causes.any { it is SocketTimeoutException } || mentions("timeout", "timed out") ->
                DetectionFailure.TIMEOUT
            mentions("connection refused", "econnrefused") ->
                DetectionFailure.REFUSED
            mentions("algorithm negotiation", "key exchange", "kex", "cipher negotiation") ->
                DetectionFailure.HANDSHAKE
            error is JSchException -> DetectionFailure.NETWORK
            else -> DetectionFailure.UNKNOWN
        }
    }
}
