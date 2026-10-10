package app.ownbridge

import app.ownbridge.core.DetectionStep
import app.ownbridge.core.InventoryParser
import app.ownbridge.core.ReadOnlyProbes
import app.ownbridge.core.SavedRunCheck
import app.ownbridge.core.SavedRunProbe
import app.ownbridge.core.ServerInventory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** One attempt, one session. This class cannot write files or change server configuration. */
internal class SshDetectionRunner {
    private val activeSession = AtomicReference<Session?>()
    private val activeChannel = AtomicReference<ChannelExec?>()
    private val commands = SshCommandExecutor(activeChannel)

    fun cancel() {
        activeChannel.getAndSet(null)?.disconnect()
        activeSession.getAndSet(null)?.disconnect()
    }

    suspend fun detect(
        host: String,
        username: String,
        password: String,
        networkRoute: SshNetworkRoute,
        savedRunId: String?,
        trustedFingerprint: String?,
        pinFirstKey: (HostIdentity) -> Boolean,
        onProgress: suspend (List<DetectionEvent>) -> Unit,
    ): DetectionUiState = withContext(Dispatchers.IO) {
        val events = mutableListOf<DetectionEvent>()
        suspend fun publish(event: DetectionEvent) {
            events += event
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }
        suspend fun replaceLast(event: DetectionEvent) {
            events[events.lastIndex] = event
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }

        if (!isValidHost(host) || username.isBlank() || password.isBlank()) {
            return@withContext DetectionUiState.Failed(events, DetectionFailure.INVALID_INPUT)
        }

        publish(DetectionEvent(
            DetectionStep.NETWORK, null, null, null, null, DetectionStepStatus.PASSED,
            networkRoute = networkRoute,
        ))

        val repository = SshHostKeyRepository(trustedFingerprint, pinFirstKey)
        val session = JSch().getSession(username.trim(), host.trim(), 22)
        activeSession.set(session)
        try {
            session.setHostKeyRepository(repository)
            session.setConfig("StrictHostKeyChecking", "yes")
            // Avoid keyboard-interactive until its prompts can be bounded and reviewed separately.
            session.setConfig("PreferredAuthentications", "password")
            session.setConfig("MaxAuthTries", "1")
            session.timeout = 10_000
            val secret = password.toByteArray(Charsets.UTF_8)
            try {
                session.setPassword(secret)
            } finally {
                secret.fill(0)
            }

            publish(DetectionEvent(DetectionStep.HOST_KEY, null, null, null, null, DetectionStepStatus.RUNNING))
            session.connect(10_000)

            replaceLast(events.last().copy(
                result = repository.observed?.let { "${it.algorithm} ${it.fingerprint}" },
                status = DetectionStepStatus.PASSED,
            ))
            publish(DetectionEvent(DetectionStep.AUTHENTICATION, null, null, null, null, DetectionStepStatus.PASSED))

            val outputs = mutableMapOf<DetectionStep, String>()
            for (probe in ReadOnlyProbes.all) {
                publish(DetectionEvent(probe.step, probe.command, null, null, null, DetectionStepStatus.RUNNING))
                val output = commands.execute(session, probe.command)
                val safeText = sanitize(output.text, password)
                replaceLast(events.last().copy(
                    result = safeText.take(MAX_VISIBLE_RESULT),
                    exitCode = output.exitCode,
                    durationMillis = output.durationMillis,
                    status = if (output.exitCode == 0) DetectionStepStatus.PASSED else DetectionStepStatus.FAILED,
                ))
                if (output.exitCode != 0) {
                    return@withContext DetectionUiState.Failed(events.toList(), DetectionFailure.COMMAND)
                }
                outputs[probe.step] = safeText
            }

            val savedRunCheck = savedRunId?.let { runId ->
                val command = SavedRunProbe.command(runId)
                publish(DetectionEvent(DetectionStep.SAVED_RUN, command, null, null, null,
                    DetectionStepStatus.RUNNING))
                val output = commands.execute(session, command)
                val presence = if (output.exitCode == 0) SavedRunProbe.parse(output.text) else null
                replaceLast(events.last().copy(
                    exitCode = output.exitCode,
                    durationMillis = output.durationMillis,
                    savedRunPresence = presence,
                    status = if (presence == null) DetectionStepStatus.FAILED else DetectionStepStatus.PASSED,
                ))
                if (presence == null) {
                    return@withContext DetectionUiState.Failed(events.toList(), DetectionFailure.COMMAND)
                }
                SavedRunCheck(runId, presence)
            }

            val system = InventoryParser.system(outputs.getValue(DetectionStep.SYSTEM))
            val (ipv4, ipv6) = InventoryParser.addresses(outputs.getValue(DetectionStep.ADDRESSES))
            if (system == "Unknown Linux" || ipv4.isEmpty()) {
                return@withContext DetectionUiState.Failed(events.toList(), DetectionFailure.COMMAND)
            }
            DetectionUiState.Ready(
                events = events.toList(),
                inventory = ServerInventory(
                    system = system,
                    ipv4Addresses = ipv4,
                    ipv6Addresses = ipv6,
                    listeningPorts = InventoryParser.ports(outputs.getValue(DetectionStep.PORTS)),
                    runningServices = InventoryParser.services(outputs.getValue(DetectionStep.SERVICES)),
                    firewall = outputs.getValue(DetectionStep.FIREWALL).trim(),
                ),
                savedRunCheck = savedRunCheck,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = SshDetectionFailureClassifier.classify(
                error, repository.changed, repository.storageFailed,
            )
            if (
                reason == DetectionFailure.AUTHENTICATION &&
                repository.observed?.fingerprint == repository.acceptedFingerprint &&
                events.lastOrNull()?.step == DetectionStep.HOST_KEY
            ) {
                val identity = repository.observed!!
                replaceLast(events.last().copy(
                    result = "${identity.algorithm} ${identity.fingerprint}",
                    status = DetectionStepStatus.PASSED,
                ))
                publish(DetectionEvent(
                    DetectionStep.AUTHENTICATION,
                    null,
                    null,
                    null,
                    null,
                    DetectionStepStatus.FAILED,
                ))
            } else if (events.isNotEmpty() && events.last().status == DetectionStepStatus.RUNNING) {
                replaceLast(events.last().copy(
                    result = repository.observed?.takeIf { repository.changed }?.fingerprint,
                    status = DetectionStepStatus.FAILED,
                ))
            }
            DetectionUiState.Failed(
                events = events.toList(),
                reason = reason,
                hostKeyChange = if (reason == DetectionFailure.HOST_KEY_CHANGED) {
                    val previous = repository.acceptedFingerprint
                    val observed = repository.observed
                    if (previous != null && observed != null) {
                        HostKeyChange(host.trim(), previous, observed)
                    } else null
                } else null,
            )
        } finally {
            session.disconnect()
            activeSession.compareAndSet(session, null)
        }
    }

    private fun isValidHost(host: String): Boolean {
        val trimmed = host.trim()
        return trimmed.length in 1..255 && trimmed.none { it.isWhitespace() || it == '/' || it == '@' }
    }

    private fun sanitize(output: String, password: String): String = output
        .filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }
        .replace(password, "[redacted]")
        .trim()

    private companion object {
        const val MAX_VISIBLE_RESULT = 8_192
    }
}
