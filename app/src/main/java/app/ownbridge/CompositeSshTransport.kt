package app.ownbridge

import app.ownbridge.core.RemoteFileStore
import app.ownbridge.core.VerifiedTransfer
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

internal class RemoteCommandFailed(val code: Int, val output: String) : Exception("Remote command failed: $code")

/** One verified SSH connection, shared by package transfer, steps and credential retrieval. */
internal class CompositeSshTransport(
    private val host: String,
    private val username: String,
    private val password: String,
    trustedFingerprint: String,
) : AutoCloseable {
    private val session = JSch().getSession(username, host, 22)
    private val activeExec = AtomicReference<ChannelExec?>()
    private val activeSftp = AtomicReference<ChannelSftp?>()
    private val commands = SshCommandExecutor(activeExec)
    private val repository = SshHostKeyRepository(trustedFingerprint) { false }

    val hostKeyChanged: Boolean get() = repository.changed
    val observedFingerprint: String? get() = repository.observed?.fingerprint

    fun connect() {
        session.setHostKeyRepository(repository)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password")
        session.setConfig("MaxAuthTries", "1")
        session.timeout = 10_000
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        try {
            session.setPassword(passwordBytes)
        } finally {
            passwordBytes.fill(0)
        }
        session.connect(10_000)
    }

    fun checked(command: String, timeoutMillis: Long = 20_000L): CommandOutput {
        val result = commands.execute(session, command, timeoutMillis, 8 * 1024)
        if (result.exitCode != 0) throw RemoteCommandFailed(result.exitCode, result.text)
        return result
    }

    fun ensureFiles(directory: String, files: List<RemoteFile>) {
        checked("install -d -m 0700 $directory")
        withSftp { sftp ->
            val transfer = VerifiedTransfer(object : RemoteFileStore {
                override fun digest(path: String): String? {
                    val result = checked(
                        "if test -f $path; then sha256sum $path; else printf 'missing\\n'; fi",
                    ).text.substringBefore(' ').trim()
                    return result.takeUnless { it == "missing" }
                }

                override fun uploadAtomically(path: String, content: ByteArray) {
                    ByteArrayInputStream(content).use { sftp.put(it, "$path.part") }
                    sftp.rename("$path.part", path)
                }
            })
            files.forEach { file -> transfer.ensure("$directory/${file.name}", file.bytes, file.sha256) }
        }
    }

    fun readSmallFile(path: String, maximumBytes: Int = 8192): ByteArray = withSftp { sftp ->
        require(sftp.lstat(path).size <= maximumBytes)
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                require(size() + length <= maximumBytes)
                super.write(bytes, offset, length)
            }

            override fun write(value: Int) {
                require(size() < maximumBytes)
                super.write(value)
            }
        }
        output.use { sftp.get(path, it); it.toByteArray() }
    }

    fun cancel() {
        activeSftp.getAndSet(null)?.disconnect()
        activeExec.getAndSet(null)?.disconnect()
        session.disconnect()
    }

    override fun close() = cancel()

    private fun <T> withSftp(block: (ChannelSftp) -> T): T {
        val channel = session.openChannel("sftp") as ChannelSftp
        activeSftp.set(channel)
        try {
            channel.connect(10_000)
            return block(channel)
        } finally {
            channel.disconnect()
            activeSftp.compareAndSet(channel, null)
        }
    }
}

internal data class RemoteFile(val name: String, val bytes: ByteArray) {
    val sha256: String = VerifiedTransfer.sha256(bytes)

    init { require(Regex("[a-z0-9][a-z0-9._-]*").matches(name)) }
}
