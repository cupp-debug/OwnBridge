package app.ownbridge

import app.ownbridge.core.CompositeDeploymentJournal
import app.ownbridge.core.CompositeDeploymentPlan
import app.ownbridge.core.CompositeJournalParser
import app.ownbridge.core.CompositeProgress
import app.ownbridge.core.CompositePlanManifest
import app.ownbridge.core.CompositeRetryPolicy
import app.ownbridge.core.CompositeStep
import app.ownbridge.core.DeployedNode
import app.ownbridge.core.ProxyProtocol
import app.ownbridge.core.StepState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

internal enum class CompositeRunAction { IDENTITY, PACKAGE, UNPACK, STEP, EXPORT, PRIOR_ATTEMPT }
internal enum class CompositeRunEventStatus { RUNNING, PASSED, FAILED }
internal enum class CompositeRunFailure {
    IDENTITY, AUTHENTICATION, NETWORK, INTERRUPTED, HASH_MISMATCH, REMOTE, EXPORT, UNKNOWN,
}

internal data class CompositeRunEvent(
    val action: CompositeRunAction,
    val step: CompositeStep?,
    val command: String?,
    val source: String?,
    val status: CompositeRunEventStatus,
    val detail: String? = null,
)

/** SERVER_READY is deliberately not a claim of real client traffic or a working subscription URL. */
internal data class CompositeRunResult(
    val journal: CompositeDeploymentJournal?,
    val nodes: List<DeployedNode>,
    val events: List<CompositeRunEvent>,
    val failure: CompositeRunFailure?,
)

/** Preview-only v2 executor. UI wiring waits for the VPS and Android traffic gates. */
internal class CompositeSshDeploymentRunner {
    private val active = AtomicReference<CompositeSshTransport?>()

    fun cancel() { active.getAndSet(null)?.cancel() }

    suspend fun run(
        plan: CompositeDeploymentPlan,
        runId: String,
        username: String,
        password: String,
        trustedFingerprint: String?,
        catalog: CompositeDeploymentPackageCatalog,
        resumeExisting: Boolean,
        oldBundleVersion: Boolean,
        onProgress: suspend (List<CompositeRunEvent>) -> Unit,
    ): CompositeRunResult = withContext(Dispatchers.IO) {
        require(Regex("[a-f0-9]{32}").matches(runId))
        require(username == "root")
        val events = mutableListOf<CompositeRunEvent>()
        suspend fun publish(event: CompositeRunEvent) {
            events += event
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }
        suspend fun finish(status: CompositeRunEventStatus, detail: String? = null) {
            events[events.lastIndex] = events.last().copy(status = status, detail = detail)
            withContext(Dispatchers.Main) { onProgress(events.toList()) }
        }

        if (trustedFingerprint.isNullOrBlank()) {
            return@withContext CompositeRunResult(null, emptyList(), events, CompositeRunFailure.IDENTITY)
        }

        val directory = "/var/lib/ownbridge/runs/$runId"
        val archive = catalog.archive()
        val planBytes = CompositePlanManifest.bytes(plan)
        val planHash = CompositePlanManifest.sha256(plan)
        val transport = CompositeSshTransport(plan.sshHost, username, password, trustedFingerprint)
        active.set(transport)
        try {
            publish(CompositeRunEvent(CompositeRunAction.IDENTITY, null, null, null, CompositeRunEventStatus.RUNNING))
            transport.connect()
            finish(CompositeRunEventStatus.PASSED, transport.observedFingerprint)

            // Finished tasks retain their original runner. Only the recognized, unstarted
            // public CRLF bundle can be restored before its status is read.
            if (resumeExisting) {
                val statusCommand = "sh $directory/package/runner.sh status $runId"
                val recovery = CompositeBundleLfRecovery(transport, catalog)
                val repaired = if (recovery.required(directory)) {
                    publish(CompositeRunEvent(CompositeRunAction.PACKAGE, null,
                        "Restore LF-only deployment package", null, CompositeRunEventStatus.RUNNING))
                    finish(CompositeRunEventStatus.PASSED, recovery.repair(directory, archive))
                    true
                } else false
                val hasRemoteRunner = transport.checked(
                    "if test -f $directory/package/runner.sh && test -f $directory/plan.txt " +
                        "&& test -f $directory/plan.sha256; then printf yes; else printf no; fi",
                ).text.trim() == "yes"
                if (hasRemoteRunner) {
                    publish(CompositeRunEvent(CompositeRunAction.EXPORT, null, statusCommand, null,
                        CompositeRunEventStatus.RUNNING))
                    val oldJournal = CompositeJournalParser.parse(transport.checked(statusCommand).text, plan)
                    if (oldJournal.overall(plan) == CompositeProgress.ALL_SERVER_READY) {
                        val oldExports = CompositeCredentialReader(transport::readSmallFile)
                            .readReady(directory, plan, oldJournal)
                        if (oldExports.failedProtocols.isNotEmpty()) {
                            finish(CompositeRunEventStatus.FAILED, "Existing client exports are incomplete")
                            return@withContext CompositeRunResult(oldJournal, oldExports.nodes,
                                events.toList(), CompositeRunFailure.EXPORT)
                        }
                        finish(CompositeRunEventStatus.PASSED,
                            "${oldExports.nodes.size} existing server-ready node(s)")
                        return@withContext CompositeRunResult(oldJournal, oldExports.nodes,
                            events.toList(), null)
                    }
                    if (oldBundleVersion && !repaired) {
                        finish(CompositeRunEventStatus.FAILED,
                            "Previous bundle is incomplete; no files or services were changed")
                        return@withContext CompositeRunResult(oldJournal, emptyList(), events.toList(),
                            CompositeRunFailure.HASH_MISMATCH)
                    }
                    finish(CompositeRunEventStatus.PASSED, "Existing run is incomplete; checking its package")
                } else if (oldBundleVersion && !repaired) {
                    return@withContext CompositeRunResult(null, emptyList(), events.toList(),
                        CompositeRunFailure.HASH_MISMATCH)
                }
            }

            publish(CompositeRunEvent(CompositeRunAction.PACKAGE, null, "SFTP $directory", null,
                CompositeRunEventStatus.RUNNING))
            transport.ensureFiles(directory, listOf(
                RemoteFile("plan.txt", planBytes),
                RemoteFile("plan.sha256", "$planHash  plan.txt\n".toByteArray(Charsets.US_ASCII)),
                RemoteFile("bundle.zip", archive.bytes),
            ))
            finish(CompositeRunEventStatus.PASSED, "SHA-256 ${archive.sha256}")

            val unpack = "python3 -m zipfile -t $directory/bundle.zip && " +
                "python3 -m zipfile -e $directory/bundle.zip $directory/package"
            publish(CompositeRunEvent(CompositeRunAction.UNPACK, null, unpack, null,
                CompositeRunEventStatus.RUNNING))
            transport.checked(unpack, 30_000L)
            finish(CompositeRunEventStatus.PASSED)

            val statusCommand = "sh $directory/package/runner.sh status $runId"
            publish(CompositeRunEvent(CompositeRunAction.EXPORT, null, statusCommand, null,
                CompositeRunEventStatus.RUNNING))
            val initial = CompositeJournalParser.parse(transport.checked(statusCommand).text, plan)
            finish(CompositeRunEventStatus.PASSED)
            suspend fun execute(step: CompositeStep): Boolean {
                val command = "sh $directory/package/runner.sh ${step.wireName} $runId"
                publish(CompositeRunEvent(CompositeRunAction.STEP, step, command, catalog.source(step),
                    CompositeRunEventStatus.RUNNING))
                return try {
                    val timeout = if (step.wireName == "shared.fetch") 240_000L else 45_000L
                    val output = transport.checked(command, timeout)
                    val observed = CompositeJournalParser.parse(
                        transport.checked(statusCommand).text, plan,
                    ).states.getValue(step)
                    if (observed != StepState.DONE) {
                        finish(CompositeRunEventStatus.FAILED, "Remote step marker is $observed")
                        return false
                    }
                    finish(CompositeRunEventStatus.PASSED, sanitize(output.text, password))
                    true
                } catch (error: RemoteCommandFailed) {
                    finish(CompositeRunEventStatus.FAILED,
                        "Exit ${error.code}: ${sanitize(error.output, password)}")
                    false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // The command may have finished on the VPS before its SSH reply was lost.
                    // A read-only marker check avoids reporting that success as a remote failure.
                    val completed = runCatching {
                        CompositeJournalParser.parse(transport.checked(statusCommand).text, plan)
                            .states.getValue(step) == StepState.DONE
                    }.getOrDefault(false)
                    if (completed) {
                        finish(CompositeRunEventStatus.PASSED)
                        true
                    } else {
                        throw error
                    }
                }
            }

            for (step in CompositeRetryPolicy.shared) {
                if (!execute(step)) {
                    val journal = CompositeJournalParser.parse(transport.checked(statusCommand).text, plan)
                    return@withContext CompositeRunResult(journal, emptyList(), events.toList(),
                        CompositeRunFailure.REMOTE)
                }
            }
            for (protocol in ProxyProtocol.entries.filter { it in plan.protocols }) {
                for (step in CompositeRetryPolicy.protocolSteps(initial, protocol)) {
                    if (!execute(step)) break // Other selected protocols remain independent.
                }
            }

            val journal = CompositeJournalParser.parse(transport.checked(statusCommand).text, plan)
            publish(CompositeRunEvent(CompositeRunAction.EXPORT, null, null, null,
                CompositeRunEventStatus.RUNNING))
            val exports = CompositeCredentialReader(transport::readSmallFile).readReady(directory, plan, journal)
            if (exports.failedProtocols.isNotEmpty()) {
                val names = exports.failedProtocols.joinToString { it.wireName }
                finish(CompositeRunEventStatus.FAILED, "Invalid client export: $names")
                return@withContext CompositeRunResult(journal, exports.nodes, events.toList(),
                    CompositeRunFailure.EXPORT)
            }
            finish(CompositeRunEventStatus.PASSED, "${exports.nodes.size} server-ready node(s)")
            CompositeRunResult(journal, exports.nodes, events.toList(), null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = CompositeRunFailureClassifier.classify(
                error, transport.hostKeyChanged,
                commandStarted = events.lastOrNull()?.action != CompositeRunAction.IDENTITY,
            )
            if (events.lastOrNull()?.status == CompositeRunEventStatus.RUNNING) {
                val detail = (error as? RemoteCommandFailed)?.let {
                    "Exit ${it.code}: ${sanitize(it.output, password)}"
                }
                finish(CompositeRunEventStatus.FAILED, detail)
            }
            // An APK update must not overwrite an immutable remote bundle. Its old runner can
            // still report whether any protocol work started, allowing a safe new task.
            val existingJournal = if (reason == CompositeRunFailure.HASH_MISMATCH) runCatching {
                val status = "sh $directory/package/runner.sh status $runId"
                CompositeJournalParser.parse(transport.checked(status).text, plan)
            }.getOrNull() else null
            CompositeRunResult(existingJournal, emptyList(), events.toList(), reason)
        } finally {
            transport.close()
            active.compareAndSet(transport, null)
        }
    }

    private fun sanitize(text: String, password: String): String =
        (if (password.isEmpty()) text else text.replace(password, "[redacted]"))
            .filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }
            .take(2_048).trim()
}
