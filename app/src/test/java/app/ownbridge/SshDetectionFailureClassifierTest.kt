package app.ownbridge

import com.jcraft.jsch.JSchException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class SshDetectionFailureClassifierTest {
    private fun classify(error: Throwable) =
        SshDetectionFailureClassifier.classify(error, hostKeyChanged = false, storageFailed = false)

    @Test fun distinguishesConnectionFailuresWithoutDisplayingLibraryMessages() {
        assertEquals(DetectionFailure.DNS, classify(JSchException("lookup", UnknownHostException())))
        assertEquals(DetectionFailure.REFUSED,
            classify(JSchException("Session.connect: Connection refused")))
        assertEquals(DetectionFailure.UNREACHABLE,
            classify(JSchException("routing", NoRouteToHostException())))
        assertEquals(DetectionFailure.TIMEOUT,
            classify(JSchException("connect", SocketTimeoutException())))
        assertEquals(DetectionFailure.TIMEOUT,
            classify(JSchException("connect", ConnectException("Connection timed out"))))
        assertEquals(DetectionFailure.NETWORK,
            classify(JSchException("connect", ConnectException("Connection reset"))))
        assertEquals(DetectionFailure.HANDSHAKE,
            classify(JSchException("Algorithm negotiation fail")))
        assertEquals(DetectionFailure.NETWORK,
            classify(JSchException("End of IO Stream Read")))
        assertEquals(DetectionFailure.AUTHENTICATION,
            classify(JSchException("Auth fail for methods 'password'")))
    }

    @Test fun hostIdentityAndStorageFailuresTakePriority() {
        val error = JSchException("Auth fail")
        assertEquals(DetectionFailure.HOST_KEY_CHANGED,
            SshDetectionFailureClassifier.classify(error, hostKeyChanged = true, storageFailed = false))
        assertEquals(DetectionFailure.STORAGE,
            SshDetectionFailureClassifier.classify(error, hostKeyChanged = false, storageFailed = true))
    }

}
