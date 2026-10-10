package app.ownbridge

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network test for a failed first-key persistence step. */
class SshHandshakeIntegrationTest {
    @Test
    fun unknownHostKeyIsRejectedIfItCannotBePersisted() {
        val host = System.getenv("OWNBRIDGE_TEST_HOST").orEmpty()
        assumeTrue(host.isNotBlank())
        val repository = SshHostKeyRepository(null) { false }
        val session = JSch().getSession("root", host, 22)
        try {
            session.setHostKeyRepository(repository)
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("PreferredAuthentications", "password")
            session.setPassword("test-placeholder-only".toByteArray())
            try {
                session.connect(10_000)
                fail("A host key that could not be persisted must not be accepted")
            } catch (_: JSchException) {
                assertNotNull("SSH negotiation must expose the untrusted host key", repository.observed)
            }
        } finally {
            session.disconnect()
        }
    }
}
