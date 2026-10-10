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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownbridge.core.DetectionStep
import app.ownbridge.core.SavedRunPresence

// Terminal colors are semantic tokens chosen for contrast against the fixed black background.
internal object TerminalPalette {
    val background = Color.Black
    val foreground = Color.White
    val separator = Color.White.copy(alpha = 0.32f)
    val success = Color(0xFF81C784)
    val failure = Color(0xFFFF8A80)
}

internal val DetectionFailure.messageRes: Int
    get() = when (this) {
        DetectionFailure.INVALID_INPUT -> R.string.failure_invalid_input
        DetectionFailure.NETWORK -> R.string.failure_network
        DetectionFailure.DNS -> R.string.failure_dns
        DetectionFailure.REFUSED -> R.string.failure_refused
        DetectionFailure.UNREACHABLE -> R.string.failure_unreachable
        DetectionFailure.HANDSHAKE -> R.string.failure_handshake
        DetectionFailure.HOST_KEY_CHANGED -> R.string.failure_host_key_changed
        DetectionFailure.AUTHENTICATION -> R.string.failure_authentication
        DetectionFailure.COMMAND -> R.string.failure_command
        DetectionFailure.TIMEOUT -> R.string.failure_timeout
        DetectionFailure.UNKNOWN -> R.string.failure_unknown
        DetectionFailure.STORAGE -> R.string.failure_storage
    }

@Composable
internal fun DetectionTerminal(state: DetectionUiState, ping: PingUiState) {
    val (status, events) = when (state) {
        DetectionUiState.Idle -> {
            if (!ping.hasLog) return
            stringResource(
                when (ping.runStatus) {
                    PingRunStatus.RUNNING -> R.string.ping_running
                    PingRunStatus.COMPLETED -> R.string.ping_completed
                    PingRunStatus.CANCELLED -> R.string.ping_cancelled
                    PingRunStatus.FAILED -> R.string.ping_failed
                    PingRunStatus.IDLE -> R.string.detection_no_result
                },
            ) to emptyList()
        }
        is DetectionUiState.Running -> stringResource(R.string.detection_status_running) to state.events
        is DetectionUiState.Cancelled -> stringResource(R.string.detection_cancelled) to state.events
        is DetectionUiState.Failed -> stringResource(
            R.string.detection_failed_with_reason,
            stringResource(state.reason.messageRes),
        ) to state.events
        is DetectionUiState.Ready -> stringResource(R.string.detection_completed) to state.events
    }
    val statusColor = when (state) {
        is DetectionUiState.Ready -> TerminalPalette.success
        is DetectionUiState.Failed -> TerminalPalette.failure
        DetectionUiState.Idle -> when (ping.runStatus) {
            PingRunStatus.COMPLETED -> if (ping.summary.received > 0) TerminalPalette.success else TerminalPalette.failure
            PingRunStatus.FAILED -> TerminalPalette.failure
            else -> TerminalPalette.foreground
        }
        else -> TerminalPalette.foreground
    }
    val statusIcon = when (state) {
        is DetectionUiState.Ready -> IconAsset.SuccessFilled
        is DetectionUiState.Failed -> IconAsset.Error
        DetectionUiState.Idle -> when (ping.runStatus) {
            PingRunStatus.COMPLETED -> if (ping.summary.received > 0) IconAsset.SuccessFilled else IconAsset.Error
            PingRunStatus.FAILED -> IconAsset.Error
            else -> null
        }
        else -> null
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TerminalText(
                        text = stringResource(R.string.detection_log),
                        modifier = Modifier.weight(1f),
                        title = true,
                    )
                    if (state is DetectionUiState.Running || ping.runStatus == PingRunStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TerminalPalette.foreground,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TerminalText("${stringResource(R.string.detection_status)}:", title = true)
                    if (statusIcon != null) {
                        ProjectIcon(
                            statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = statusColor,
                        )
                    }
                    TerminalText(status, modifier = Modifier.weight(1f), color = statusColor)
                }
                if (state.showVpnWarning()) {
                    val iconSize = with(LocalDensity.current) {
                        MaterialTheme.typography.labelLarge.fontSize.toDp()
                    }
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProjectIcon(
                            IconAsset.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = TerminalPalette.failure,
                        )
                        TerminalText(
                            stringResource(R.string.detection_vpn_warning),
                            modifier = Modifier.weight(1f),
                            title = true,
                            color = TerminalPalette.failure,
                        )
                    }
                }
                HorizontalDivider(color = TerminalPalette.separator)
                if (state !is DetectionUiState.Idle) EventList(events)
                if (ping.hasLog) PingLog(ping, showSectionTitle = state !is DetectionUiState.Idle)
            }
        }
    }
}

@Composable
private fun EventList(events: List<DetectionEvent>) {
    events.forEachIndexed { index, event ->
        if (index > 0) HorizontalDivider(color = TerminalPalette.separator)
        TerminalText(
            text = stringResource(R.string.detection_step_number, index + 1, events.size),
            title = true,
        )
        TerminalField(
            stringResource(R.string.detection_purpose),
            stringResource(
                when (event.step) {
                    DetectionStep.NETWORK -> R.string.purpose_network
                    DetectionStep.HOST_KEY -> R.string.purpose_host_key
                    DetectionStep.AUTHENTICATION -> R.string.purpose_authentication
                    DetectionStep.SYSTEM -> R.string.purpose_system
                    DetectionStep.ADDRESSES -> R.string.purpose_addresses
                    DetectionStep.PORTS -> R.string.purpose_ports
                    DetectionStep.SERVICES -> R.string.purpose_services
                    DetectionStep.FIREWALL -> R.string.purpose_firewall
                    DetectionStep.SAVED_RUN -> R.string.purpose_saved_run
                },
            ),
        )
        TerminalField(
            stringResource(R.string.detection_sent),
            event.command ?: stringResource(R.string.detection_local_action),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (event.status == DetectionStepStatus.RUNNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = TerminalPalette.foreground,
                    strokeWidth = 2.dp,
                )
            }
            TerminalText(
                text = when (event.status) {
                    DetectionStepStatus.PENDING -> stringResource(R.string.detection_pending)
                    DetectionStepStatus.RUNNING -> stringResource(R.string.detection_running)
                    DetectionStepStatus.PASSED -> stringResource(R.string.detection_passed)
                    DetectionStepStatus.FAILED -> stringResource(R.string.detection_failed)
                    DetectionStepStatus.CANCELLED -> stringResource(R.string.detection_cancelled)
                },
            )
        }
        TerminalField(
            stringResource(R.string.detection_result),
            event.networkRoute?.let { route ->
                stringResource(when (route) {
                    SshNetworkRoute.VPN -> R.string.network_route_vpn
                    SshNetworkRoute.OTHER -> R.string.network_route_other
                    SshNetworkRoute.UNKNOWN -> R.string.network_route_unknown
                })
            } ?: event.savedRunPresence?.let { presence ->
                stringResource(when (presence) {
                    SavedRunPresence.PRESENT -> R.string.saved_run_present
                    SavedRunPresence.MISSING -> R.string.saved_run_missing
                })
            } ?: event.result ?: stringResource(
                when (event.status) {
                    DetectionStepStatus.PENDING, DetectionStepStatus.RUNNING -> R.string.detection_no_result
                    DetectionStepStatus.PASSED -> R.string.detection_passed
                    DetectionStepStatus.FAILED -> R.string.detection_failed
                    DetectionStepStatus.CANCELLED -> R.string.detection_cancelled
                },
            ),
        )
        event.exitCode?.let { code ->
            TerminalField(stringResource(R.string.detection_exit_code), code.toString())
        }
        event.durationMillis?.let { duration ->
            TerminalField(
                stringResource(R.string.detection_duration),
                stringResource(R.string.detection_milliseconds, duration),
            )
        }
    }
}

@Composable
internal fun TerminalField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TerminalText("$label:", title = true)
        TerminalText(value)
    }
}

@Composable
internal fun TerminalText(
    text: String,
    modifier: Modifier = Modifier,
    title: Boolean = false,
    color: Color = TerminalPalette.foreground,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = if (title) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (title) MaterialTheme.typography.containerTitle.fontWeight else FontWeight.Normal,
    )
}
