package app.ownbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyChangeTest {
    private val change = HostKeyChange(
        host = "vps.example",
        previousFingerprint = "SHA256:old",
        observed = HostIdentity("SHA256:new", "ssh-ed25519"),
    )

    @Test fun confirmationAppliesOnlyToTheHostAndOldKeyThatFailed() {
        assertTrue(change.matches("VPS.EXAMPLE", "SHA256:old"))
        assertFalse(change.matches("other.example", "SHA256:old"))
        assertFalse(change.matches("vps.example", "SHA256:different"))
        assertFalse(change.matches("vps.example", null))
    }
}
