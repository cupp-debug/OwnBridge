package app.ownbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedTransferTest {
    @Test fun uploadsOnceAndAcceptsExactRetry() {
        val remote = FakeRemote()
        val transfer = VerifiedTransfer(remote)
        val content = "fixed package".toByteArray()
        transfer.ensure("bundle.zip", content)
        transfer.ensure("bundle.zip", content)
        assertEquals(1, remote.uploads)
    }

    @Test fun refusesPreexistingOrInTransitCorruption() {
        val remote = FakeRemote(mutableMapOf("bundle.zip" to "different".toByteArray()))
        assertThrows(DigestMismatchException::class.java) {
            VerifiedTransfer(remote).ensure("bundle.zip", "fixed package".toByteArray())
        }
        assertEquals(0, remote.uploads)

        remote.files.clear()
        remote.corruptUpload = true
        assertThrows(DigestMismatchException::class.java) {
            VerifiedTransfer(remote).ensure("bundle.zip", "fixed package".toByteArray())
        }
    }

    private class FakeRemote(
        val files: MutableMap<String, ByteArray> = mutableMapOf(),
    ) : RemoteFileStore {
        var uploads = 0
        var corruptUpload = false

        override fun digest(path: String): String? = files[path]?.let(VerifiedTransfer::sha256)

        override fun uploadAtomically(path: String, content: ByteArray) {
            uploads++
            files[path] = if (corruptUpload) byteArrayOf(0) else content.copyOf()
        }
    }
}
