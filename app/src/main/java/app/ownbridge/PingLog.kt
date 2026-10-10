package app.ownbridge

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun PingLog(state: PingUiState, showSectionTitle: Boolean) {
    if (showSectionTitle) {
        HorizontalDivider(color = TerminalPalette.separator)
        TerminalText(stringResource(R.string.ping_log), title = true)
    }
    TerminalField(stringResource(R.string.ping_purpose), stringResource(R.string.ping_purpose_value))
    state.target?.let { TerminalField(stringResource(R.string.ping_target), it) }
    state.command?.let { TerminalField(stringResource(R.string.detection_sent), it) }
    state.samples.forEachIndexed { index, sample ->
        val value = sample.latencyMs?.let { stringResource(R.string.ping_latency, it) }
            ?: stringResource(R.string.ping_timeout)
        TerminalText(stringResource(R.string.ping_attempt, index + 1, value))
    }
    state.failure?.let {
        TerminalText(
            stringResource(if (it == PingFailure.ADDRESS) R.string.ping_address_failure else R.string.ping_command_failure),
            color = TerminalPalette.failure,
        )
    }
    val summary = state.summary
    TerminalField(
        stringResource(R.string.ping_summary),
        stringResource(R.string.ping_counts, summary.samples.size, summary.received, summary.lossPercent),
    )
    TerminalField(
        stringResource(R.string.ping_average),
        summary.averageMs?.let { stringResource(R.string.ping_latency, it) }
            ?: stringResource(R.string.ping_no_average),
    )
}
