package app.ownbridge

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

/** Automatically pins a first key when persistence succeeds; later changes are rejected. */
internal class SshHostKeyRepository(
    expectedFingerprint: String?,
    private val pinFirstKey: (HostIdentity) -> Boolean,
) : HostKeyRepository {
    var acceptedFingerprint: String? = expectedFingerprint
        private set
    var observed: HostIdentity? = null
        private set
    var changed: Boolean = false
        private set
    var storageFailed: Boolean = false
        private set

    override fun check(host: String, key: ByteArray): Int {
        val algorithmLength = if (key.size >= 4) {
            ((key[0].toInt() and 0xff) shl 24) or
                ((key[1].toInt() and 0xff) shl 16) or
                ((key[2].toInt() and 0xff) shl 8) or
                (key[3].toInt() and 0xff)
        } else 0
        val algorithm = if (algorithmLength in 1..64 && key.size >= 4 + algorithmLength) {
            String(key, 4, algorithmLength, Charsets.US_ASCII)
        } else "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        observed = HostIdentity(fingerprint, algorithm)
        return when {
            acceptedFingerprint == null -> {
                if (pinFirstKey(observed!!)) {
                    acceptedFingerprint = fingerprint
                    HostKeyRepository.OK
                } else {
                    storageFailed = true
                    HostKeyRepository.NOT_INCLUDED
                }
            }
            acceptedFingerprint == fingerprint -> HostKeyRepository.OK
            else -> {
                changed = true
                HostKeyRepository.CHANGED
            }
        }
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
    override fun remove(host: String, type: String?) = Unit
    override fun remove(host: String, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "OwnBridge session pin"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
