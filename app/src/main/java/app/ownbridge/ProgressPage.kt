package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment

@Composable
internal fun ProgressPage(state: DeploymentState) {
    val events = when (state) {
        DeploymentState.Idle -> emptyList()
        is DeploymentState.Running -> state.events
        is DeploymentState.Failed -> state.events
        is DeploymentState.ServerReady -> state.events
    }
    val status = when (state) {
        DeploymentState.Idle -> stringResource(R.string.pending_execution)
        is DeploymentState.Running -> stringResource(R.string.deployment_running)
        is DeploymentState.Failed -> stringResource(state.reason.messageRes)
        is DeploymentState.ServerReady -> stringResource(R.string.deployment_server_ready)
    }
    val statusColor = when (state) {
        is DeploymentState.Failed -> TerminalPalette.failure
        is DeploymentState.ServerReady -> TerminalPalette.success
        else -> TerminalPalette.foreground
    }
    if (events.isEmpty()) {
        when (state) {
            is DeploymentState.Failed -> Text(status, color = MaterialTheme.colorScheme.error)
            is DeploymentState.ServerReady -> Text(status, color = MaterialTheme.colorScheme.primary)
            DeploymentState.Idle,
            is DeploymentState.Running -> Unit
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TerminalPalette.background,
        contentColor = TerminalPalette.foreground,
        shape = RectangleShape,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TerminalText(stringResource(R.string.deployment_log), title = true)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalText("${stringResource(R.string.deployment_status)}:", title = true)
                    if (state is DeploymentState.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = TerminalPalette.foreground,
                        )
                    }
                    TerminalText(status, color = statusColor)
                }
                events.forEachIndexed { index, event ->
                    HorizontalDivider(color = TerminalPalette.separator)
                    TerminalText(
                        stringResource(R.string.detection_step_number, index + 1, events.size),
                        title = true,
                    )
                    TerminalField(
                        stringResource(R.string.deployment_action),
                        if (event.action == DeploymentAction.STEP) event.step!!.title else stringResource(
                            when (event.action) {
                                DeploymentAction.IDENTITY -> R.string.deployment_identity
                                DeploymentAction.PACKAGE -> R.string.deployment_package
                                DeploymentAction.UNPACK -> R.string.deployment_unpack
                                DeploymentAction.STEP -> error("Handled above")
                                DeploymentAction.EXTERNAL -> R.string.deployment_external
                                DeploymentAction.PRIOR_ATTEMPT -> R.string.progress_prior_attempt
                            },
                        ),
                    )
                    event.command?.let { TerminalField(stringResource(R.string.detection_sent), it) }
                    event.source?.let { TerminalField(stringResource(R.string.deployment_source), it) }
                    val resultMessage = if (event.message == null) null
                        else stringResource(event.message.messageRes)
                    val result = listOfNotNull(resultMessage, event.result).joinToString("\n")
                    val fallback = stringResource(when {
                        event.action == DeploymentAction.PRIOR_ATTEMPT -> R.string.progress_prior_attempt_detail
                        event.status == DeploymentEventStatus.RUNNING -> R.string.deployment_waiting
                        else -> R.string.detection_failed
                    })
                    TerminalField(
                        stringResource(R.string.detection_result),
                        if (result.isNotEmpty()) result else fallback,
                    )
                }
            }
        }
    }
}

private val DeploymentMessage.messageRes: Int
    get() = when (this) {
        DeploymentMessage.PACKAGE_VERIFIED -> R.string.deployment_package_verified
        DeploymentMessage.UNPACKED -> R.string.deployment_unpacked
        DeploymentMessage.PREVIOUSLY_COMPLETED -> R.string.deployment_previously_completed
        DeploymentMessage.EXTERNAL_REACHED -> R.string.deployment_external_reached
    }

internal val DeploymentFailure.messageRes: Int
    get() = when (this) {
        DeploymentFailure.IDENTITY -> R.string.failure_host_key_changed
        DeploymentFailure.AUTHENTICATION -> R.string.failure_authentication
        DeploymentFailure.NETWORK -> R.string.failure_network
        DeploymentFailure.TRANSFER -> R.string.deployment_transfer_failed
        DeploymentFailure.HASH_MISMATCH -> R.string.deployment_hash_failed
        DeploymentFailure.UNPACK -> R.string.deployment_unpack_failed
        DeploymentFailure.REMOTE_STEP -> R.string.deployment_step_failed
        DeploymentFailure.EXTERNAL_REACHABILITY -> R.string.deployment_external_failed
        DeploymentFailure.STORAGE -> R.string.deployment_storage_failed
        DeploymentFailure.UNKNOWN -> R.string.deployment_unknown_failed
    }
