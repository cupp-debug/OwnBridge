package app.ownbridge

import app.ownbridge.core.DigestMismatchException
import com.jcraft.jsch.JSchException
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeRunFailureClassifierTest {
    private fun classify(error: Throwable, commandStarted: Boolean = true) =
        CompositeRunFailureClassifier.classify(error, hostKeyChanged = false, commandStarted)

    @Test fun uncertainSshDeliveryIsNotReportedAsServerFailure() {
        assertEquals(CompositeRunFailure.INTERRUPTED,
            classify(SshCommandExecutor.CommandTimeoutException()))
        assertEquals(CompositeRunFailure.INTERRUPTED,
            classify(SshCommandExecutor.UnknownExitStatusException()))
        assertEquals(CompositeRunFailure.INTERRUPTED,
            classify(JSchException("session is down")))
        assertEquals(CompositeRunFailure.NETWORK,
            classify(JSchException("session is down"), commandStarted = false))
    }

    @Test fun confirmedRemoteFailureAndIdentityRemainDistinct() {
        assertEquals(CompositeRunFailure.REMOTE, classify(RemoteCommandFailed(1, "")))
        assertEquals(CompositeRunFailure.HASH_MISMATCH, classify(DigestMismatchException()))
        assertEquals(CompositeRunFailure.AUTHENTICATION, classify(JSchException("Auth fail")))
        assertEquals(CompositeRunFailure.IDENTITY,
            CompositeRunFailureClassifier.classify(Exception(), hostKeyChanged = true,
                commandStarted = false))
        assertEquals(CompositeRunFailure.UNKNOWN, classify(IllegalArgumentException()))
    }
}
