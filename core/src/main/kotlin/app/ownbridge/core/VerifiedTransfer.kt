package app.ownbridge.core

import java.security.MessageDigest

interface RemoteFileStore {
    fun digest(path: String): String?
    fun uploadAtomically(path: String, content: ByteArray)
}

class DigestMismatchException : Exception()

class VerifiedTransfer(private val remote: RemoteFileStore) {
    fun ensure(path: String, content: ByteArray, expectedSha256: String = sha256(content)) {
        if (sha256(content) != expectedSha256) throw DigestMismatchException()
        when (remote.digest(path)) {
            null -> remote.uploadAtomically(path, content)
            expectedSha256 -> Unit
            else -> throw DigestMismatchException()
        }
        if (remote.digest(path) != expectedSha256) throw DigestMismatchException()
    }

    companion object {
        fun sha256(content: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(content).joinToString("") { "%02x".format(it) }
    }
}
