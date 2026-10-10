package app.ownbridge

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal data class CommandOutput(val text: String, val exitCode: Int, val durationMillis: Long)

/** Bounds output and execution time even when the remote host is uncooperative. */
internal class SshCommandExecutor(private val activeChannel: AtomicReference<ChannelExec?>) {
    fun execute(
        session: Session,
        command: String,
        timeoutMillis: Long = 15_000L,
        maxOutputBytes: Int = 32 * 1024,
    ): CommandOutput {
        val channel = session.openChannel("exec") as ChannelExec
        activeChannel.set(channel)
        val stderr = LimitedOutputStream(maxOutputBytes)
        val stdout = LimitedOutputStream(maxOutputBytes)
        val started = System.nanoTime()
        try {
            channel.setCommand(command)
            channel.setInputStream(null)
            channel.setErrStream(stderr)
            val input = channel.inputStream
            channel.connect(CONNECT_TIMEOUT_MS)
            val buffer = ByteArray(4096)
            while (!channel.isClosed || input.available() > 0) {
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > timeoutMillis) {
                    throw CommandTimeoutException()
                }
                val available = input.available()
                if (available > 0) {
                    val count = input.read(buffer, 0, min(buffer.size, available))
                    if (count > 0) stdout.write(buffer, 0, count)
                } else {
                    Thread.sleep(30)
                }
                if (stdout.overflow || stderr.overflow) throw OutputLimitException()
            }
            val result = stdout.toString() + stderr.toString()
            if (channel.exitStatus < 0) throw UnknownExitStatusException()
            return CommandOutput(
                text = result,
                exitCode = channel.exitStatus,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            )
        } finally {
            channel.disconnect()
            activeChannel.compareAndSet(channel, null)
        }
    }

    private class LimitedOutputStream(private val limit: Int) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        @Volatile var overflow = false
            private set

        @Synchronized
        override fun write(value: Int) {
            if (buffer.size() < limit) buffer.write(value) else overflow = true
        }

        @Synchronized
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            val allowed = min(length, limit - buffer.size())
            if (allowed > 0) buffer.write(bytes, offset, allowed)
            if (allowed < length) overflow = true
        }

        @Synchronized
        override fun toString(): String = buffer.toString(Charsets.UTF_8.name())
    }

    internal class CommandTimeoutException : Exception()
    internal class OutputLimitException : Exception()
    internal class UnknownExitStatusException : Exception()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
