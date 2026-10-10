package app.ownbridge

import app.ownbridge.core.DeploymentJournalParser
import app.ownbridge.core.DeploymentStep
import app.ownbridge.core.DigestMismatchException
import app.ownbridge.core.RemoteFileStore
import app.ownbridge.core.ShadowsocksPlan
import app.ownbridge.core.VerifiedTransfer
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

internal class SshDeploymentRunner {
    private val activeSession = AtomicReference<Session?>()
    private val activeExec = AtomicReference<ChannelExec?>()
    private val activeSftp = AtomicReference<ChannelSftp?>()
    private val commands = SshCommandExecutor(activeExec)

    fun cancel() {
        activeSftp.getAndSet(null)?.disconnect()
        activeExec.getAndSet(null)?.disconnect()
        activeSession.getAndSet(null)?.disconnect()
    }

    suspend fun run(
        plan: ShadowsocksPlan,
        runId: String,
        username: String,
        password: String,
        trustedFingerprint: String?,
        catalog: DeploymentPackageCatalog,
        onProgress: suspend (List<DeploymentEvent>) -> Unit,
    ): DeploymentState = withContext(Dispatchers.IO) {
        require(runId.matches(Regex("[a-f0-9]{32}")))
        require(plan.sshHost.isNotBlank() && plan.address.isNotBlank())
        val events = mutableListOf<DeploymentEvent>()
        suspend fun publish(event: DeploymentEvent) {
            events += event
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }
        suspend fun finish(
            status: DeploymentEventStatus,
            result: String? = null,
            message: DeploymentMessage? = null,
        ) {
            events[events.lastIndex] = events.last().copy(status = status, result = result, message = message)
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }

        if (trustedFingerprint == null) {
            return@withContext DeploymentState.Failed(runId, plan, events, DeploymentFailure.IDENTITY)
        }
        val directory = "/var/lib/ownbridge/runs/$runId"
        val archive = catalog.archive()
        val planBytes = "${plan.address}\n${plan.port}\n".toByteArray(Charsets.US_ASCII)
        val repository = SshHostKeyRepository(trustedFingerprint) { false }
        val session = JSch().getSession(username, plan.sshHost, 22)
        activeSession.set(session)
        var phase = DeploymentFailure.NETWORK
        try {
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
            publish(DeploymentEvent(
                DeploymentAction.IDENTITY, null, null, null, null, null, DeploymentEventStatus.RUNNING,
            ))
            session.connect(10_000)
            finish(DeploymentEventStatus.PASSED, repository.observed?.fingerprint)

            phase = DeploymentFailure.TRANSFER
            publish(DeploymentEvent(
                DeploymentAction.PACKAGE, null,
                "SFTP $directory/bundle.zip; SHA-256 ${archive.sha256}",
                null, null, null, DeploymentEventStatus.RUNNING,
            ))
            checked(session, "install -d -m 0700 $directory")
            val sftp = session.openChannel("sftp") as ChannelSftp
            activeSftp.set(sftp)
            try {
                sftp.connect(10_000)
                val transfer = VerifiedTransfer(object : RemoteFileStore {
                    override fun digest(path: String): String? {
                        val value = checked(
                            session,
                            "if test -f $path; then sha256sum $path; else printf 'missing\\n'; fi",
                        ).text.substringBefore(' ').trim()
                        return value.takeUnless { it == "missing" }
                    }

                    override fun uploadAtomically(path: String, content: ByteArray) {
                        upload(sftp, content, "$path.part")
                        sftp.rename("$path.part", path)
                    }
                })
                transfer.ensure("$directory/plan.txt", planBytes)
                transfer.ensure("$directory/bundle.zip", archive.bytes, archive.sha256)
            } finally {
                sftp.disconnect()
                activeSftp.compareAndSet(sftp, null)
            }
            finish(DeploymentEventStatus.PASSED, "SHA-256 ${archive.sha256}", DeploymentMessage.PACKAGE_VERIFIED)

            phase = DeploymentFailure.UNPACK
            publish(DeploymentEvent(
                DeploymentAction.UNPACK, null,
                "unzip -oq $directory/bundle.zip -d $directory/package",
                null, null, null, DeploymentEventStatus.RUNNING,
            ))
            checked(session, "unzip -tqq $directory/bundle.zip && unzip -oq $directory/bundle.zip -d $directory/package")
            finish(DeploymentEventStatus.PASSED, message = DeploymentMessage.UNPACKED)

            phase = DeploymentFailure.REMOTE_STEP
            val statusCommand = "sh $directory/package/runner.sh status $runId"
            val journal = DeploymentJournalParser.parse(checked(session, statusCommand).text)
            for (step in DeploymentStep.entries) {
                val command = "sh $directory/package/runner.sh ${step.wireName} $runId"
                publish(DeploymentEvent(
                    DeploymentAction.STEP, step, command, catalog.source(step),
                    null, null, DeploymentEventStatus.RUNNING,
                ))
                val prior = journal.states.getValue(step)
                if (prior == app.ownbridge.core.StepState.DONE && step != DeploymentStep.VERIFY) {
                    finish(DeploymentEventStatus.PASSED, message = DeploymentMessage.PREVIOUSLY_COMPLETED)
                } else {
                    val output = checked(
                        session, command, if (step == DeploymentStep.FETCH) 240_000L else 45_000L,
                    )
                        .text.let { sanitize(it, password) }
                    finish(DeploymentEventStatus.PASSED, output)
                }
            }

            phase = DeploymentFailure.EXTERNAL_REACHABILITY
            publish(DeploymentEvent(
                DeploymentAction.EXTERNAL, null,
                "${plan.address}:${plan.port}",
                null, null, null, DeploymentEventStatus.RUNNING,
            ))
            Socket().use { it.connect(InetSocketAddress(plan.address, plan.port), 5_000) }
            finish(DeploymentEventStatus.PASSED, message = DeploymentMessage.EXTERNAL_REACHED)

            phase = DeploymentFailure.TRANSFER
            val secret = (session.openChannel("sftp") as ChannelSftp).let { sftp ->
                activeSftp.set(sftp)
                try {
                    sftp.connect(10_000)
                    ByteArrayOutputStream().use { buffer ->
                        sftp.get("$directory/secret.txt", buffer)
                        buffer.toString(Charsets.US_ASCII.name()).trim()
                    }
                } finally {
                    sftp.disconnect()
                    activeSftp.compareAndSet(sftp, null)
                }
            }
            require(secret.length in 32..128)
            DeploymentState.ServerReady(runId, plan, events.toList(), secret)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = when {
                repository.changed -> DeploymentFailure.IDENTITY
                error is DigestMismatchException -> DeploymentFailure.HASH_MISMATCH
                error.message?.contains("auth fail", ignoreCase = true) == true -> DeploymentFailure.AUTHENTICATION
                error is JSchException && phase == DeploymentFailure.NETWORK -> DeploymentFailure.NETWORK
                else -> phase
            }
            if (events.lastOrNull()?.status == DeploymentEventStatus.RUNNING) {
                val detail = if (error is RemoteStepException) sanitize(error.message.orEmpty(), password)
                    else error.javaClass.simpleName
                finish(DeploymentEventStatus.FAILED, detail)
            }
            DeploymentState.Failed(runId, plan, events.toList(), reason)
        } finally {
            session.disconnect()
            activeSession.compareAndSet(session, null)
        }
    }

    private fun upload(sftp: ChannelSftp, bytes: ByteArray, path: String) {
        ByteArrayInputStream(bytes).use { sftp.put(it, path) }
    }

    private fun checked(session: Session, command: String, timeoutMillis: Long = 20_000L): CommandOutput {
        val output = commands.execute(session, command, timeoutMillis, 8 * 1024)
        if (output.exitCode != 0) throw RemoteStepException(output.exitCode, output.text)
        return output
    }

    private fun sanitize(text: String, password: String): String =
        (if (password.isEmpty()) text else text.replace(password, "[redacted]"))
        .filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }
        .replace(Regex("(?i)(password[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\\"'\\s,}]+"), "$1[redacted]")
        .take(2_048)
        .trim()

    private class RemoteStepException(exitCode: Int, output: String) : Exception(
        "Exit code $exitCode\n${output.take(2_048)}",
    )
}
