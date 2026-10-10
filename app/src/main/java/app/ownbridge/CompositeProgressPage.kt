package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ownbridge.core.CompositeProgress

@Composable
internal fun CompositeProgressPage(state: CompositeDeploymentState) {
    val events = when (state) {
        is CompositeDeploymentState.Running -> state.events
        is CompositeDeploymentState.Settled -> state.result.events
        else -> emptyList()
    }
    val statusRes = when (state) {
        CompositeDeploymentState.Idle -> R.string.pending_execution
        CompositeDeploymentState.LocalRecordCorrupt -> R.string.plan_local_record_corrupt
        is CompositeDeploymentState.NeedsReconciliation -> R.string.deployment_record_needs_check
        is CompositeDeploymentState.Running -> R.string.deployment_running
        is CompositeDeploymentState.Settled -> {
            val result = state.result
            when {
                result.failure != null -> result.failure.messageRes
                result.journal == null -> R.string.deployment_unknown_failed
                else -> when (result.journal.overall(state.record.plan)) {
                    CompositeProgress.ALL_SERVER_READY -> R.string.deployment_server_ready
                    CompositeProgress.PARTIAL -> R.string.composite_partial
                    CompositeProgress.UNCERTAIN -> R.string.composite_uncertain
                    CompositeProgress.FAILED -> R.string.composite_failed
                    CompositeProgress.PENDING -> R.string.composite_pending
                }
            }
        }
    }
    val color = when (state) {
        is CompositeDeploymentState.Settled ->
            if (state.result.journal?.overall(state.record.plan) == CompositeProgress.ALL_SERVER_READY &&
                state.result.failure == null) TerminalPalette.success else TerminalPalette.failure
        CompositeDeploymentState.LocalRecordCorrupt -> TerminalPalette.failure
        else -> TerminalPalette.foreground
    }
    if (events.isEmpty()) {
        when (state) {
            CompositeDeploymentState.LocalRecordCorrupt -> Text(
                stringResource(statusRes), color = MaterialTheme.colorScheme.error)
            is CompositeDeploymentState.Settled -> Text(
                stringResource(statusRes),
                color = if (state.result.failure == null &&
                    state.result.journal?.overall(state.record.plan) == CompositeProgress.ALL_SERVER_READY
                ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            is CompositeDeploymentState.NeedsReconciliation -> Text(
                stringResource(statusRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompositeDeploymentState.Idle,
            is CompositeDeploymentState.Running -> Unit
        }
        return
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = TerminalPalette.background,
        contentColor = TerminalPalette.foreground, shape = RectangleShape) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TerminalText(stringResource(R.string.deployment_log), title = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TerminalText("${stringResource(R.string.deployment_status)}:", title = true)
                    if (state is CompositeDeploymentState.Running) CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = TerminalPalette.foreground,
                    )
                    TerminalText(stringResource(statusRes), color = color)
                }
                events.forEachIndexed { index, event ->
                    HorizontalDivider(color = TerminalPalette.separator)
                    TerminalText(stringResource(R.string.detection_step_number, index + 1, events.size), title = true)
                    TerminalField(stringResource(R.string.deployment_action),
                        event.step?.wireName ?: stringResource(when (event.action) {
                            CompositeRunAction.IDENTITY -> R.string.deployment_identity
                            CompositeRunAction.PACKAGE -> R.string.deployment_package
                            CompositeRunAction.UNPACK -> R.string.deployment_unpack
                            CompositeRunAction.EXPORT -> R.string.composite_client_export
                            CompositeRunAction.STEP -> R.string.deployment_step_failed
                            CompositeRunAction.PRIOR_ATTEMPT -> R.string.progress_prior_attempt
                        }))
                    event.command?.let { TerminalField(stringResource(R.string.detection_sent), it) }
                    event.source?.let { TerminalField(stringResource(R.string.deployment_source), it) }
                    val resultRes = when (event.status) {
                        CompositeRunEventStatus.RUNNING -> R.string.deployment_waiting
                        CompositeRunEventStatus.PASSED -> R.string.composite_step_passed
                        CompositeRunEventStatus.FAILED -> if (state is CompositeDeploymentState.Settled &&
                            index == events.lastIndex && state.result.failure != null) statusRes
                            else R.string.detection_failed
                    }
                    TerminalField(stringResource(R.string.detection_result), event.detail ?:
                        stringResource(if (event.action == CompositeRunAction.PRIOR_ATTEMPT)
                            R.string.progress_prior_attempt_detail else resultRes))
                }
            }
        }
    }
}

internal val CompositeRunFailure.messageRes: Int
    get() = when (this) {
        CompositeRunFailure.IDENTITY -> R.string.failure_host_key_changed
        CompositeRunFailure.AUTHENTICATION -> R.string.failure_authentication
        CompositeRunFailure.NETWORK -> R.string.failure_network
        CompositeRunFailure.INTERRUPTED -> R.string.composite_ssh_interrupted
        CompositeRunFailure.HASH_MISMATCH -> R.string.deployment_hash_failed
        CompositeRunFailure.REMOTE -> R.string.deployment_step_failed
        CompositeRunFailure.EXPORT -> R.string.composite_export_failed
        CompositeRunFailure.UNKNOWN -> R.string.deployment_unknown_failed
    }
