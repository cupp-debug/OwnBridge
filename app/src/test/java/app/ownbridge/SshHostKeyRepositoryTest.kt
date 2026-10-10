package app.ownbridge

import com.jcraft.jsch.HostKeyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshHostKeyRepositoryTest {
    private val key = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".toByteArray() + byteArrayOf(1, 2, 3)

    @Test
    fun firstKeyIsRejectedWhenItCannotBePinned() {
        val repository = SshHostKeyRepository(null) { false }

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("example.com", key))
        assertTrue(repository.storageFailed)
        assertEquals("ssh-ed25519", repository.observed?.algorithm)
        assertTrue(repository.observed?.fingerprint?.startsWith("SHA256:") == true)
    }

    @Test
    fun firstKeyIsPinnedOnceAndLaterChangesAreRejected() {
        val pinned = mutableListOf<HostIdentity>()
        val repository = SshHostKeyRepository(null) { identity ->
            pinned += identity
            true
        }

        assertEquals(HostKeyRepository.OK, repository.check("example.com", key))
        assertEquals(HostKeyRepository.OK, repository.check("example.com", key))
        assertEquals(1, pinned.size)
        assertEquals(pinned.single().fingerprint, repository.acceptedFingerprint)
        assertFalse(repository.changed)
        assertEquals(HostKeyRepository.CHANGED, repository.check("example.com", key + 4.toByte()))
        assertTrue(repository.changed)
        assertEquals(1, pinned.size)
    }

    @Test
    fun savedKeyIsAcceptedWithoutPinningAgain() {
        val first = SshHostKeyRepository(null) { true }
        assertEquals(HostKeyRepository.OK, first.check("example.com", key))
        val saved = SshHostKeyRepository(first.acceptedFingerprint) { false }

        assertEquals(HostKeyRepository.OK, saved.check("example.com", key))
        assertFalse(saved.storageFailed)
    }

    @Test
    fun confirmedKeyRejectsAnyFurtherChangeOnRetry() {
        val newKey = key + 4.toByte()
        val afterConfirmation = SshHostKeyRepository(null) { true }
        assertEquals(HostKeyRepository.OK, afterConfirmation.check("example.com", newKey))

        val retry = SshHostKeyRepository(afterConfirmation.acceptedFingerprint) { false }
        assertEquals(HostKeyRepository.OK, retry.check("example.com", newKey))
        assertEquals(HostKeyRepository.CHANGED, retry.check("example.com", newKey + 5.toByte()))
    }
}
